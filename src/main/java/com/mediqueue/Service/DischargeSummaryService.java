package com.mediqueue.Service;

import com.mediqueue.dto.DischargeSummaryRequest;
import com.mediqueue.dto.DischargeSummaryResponse;
import com.mediqueue.entity.*;
import com.mediqueue.exception.ConflictException;
import com.mediqueue.exception.ResourceNotFoundException;
import com.mediqueue.repository.AppointmentRepository;
import com.mediqueue.repository.DischargeSummaryAuditRepository;
import com.mediqueue.repository.DischargeSummaryRepository;
import com.mediqueue.repository.DoctorRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.mediqueue.ai.DietPrompt;
import com.mediqueue.ai.DietPromptBuilder;
import com.mediqueue.dto.DietRequestContext;
import com.mediqueue.repository.AiGenerationLogRepository;
import com.mediqueue.repository.PatientHealthProfileRepository;
import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class DischargeSummaryService {

    // A summary describes a consultation that happened (or is happening). Not for PENDING or NO_SHOW.
    private static final Set<AppointmentStatus> WRITABLE =
            EnumSet.of(AppointmentStatus.IN_PROGRESS, AppointmentStatus.COMPLETED);
    public static final String DIET_DISCLAIMER =
            "AI-generated general diet guidance, not medical advice. Confirm with your doctor before changing your diet.";
    private static final int MAX_DIET_GENERATIONS_PER_VISIT = 5;
    private final DischargeSummaryRepository summaryRepository;
    private final DischargeSummaryAuditRepository auditRepository;
    private final AppointmentRepository appointmentRepository;
    private final DoctorRepository doctorRepository;
    private final PatientHealthProfileRepository profileRepository;
    private final AiGenerationLogRepository aiLogRepository;
    private final AiGenerationLogWriter aiLogWriter;

    @Transactional
    public DischargeSummaryResponse saveForDoctor(User doctorUser, Long appointmentId, DischargeSummaryRequest request) {
        Doctor doctor = doctorRepository.findByUserId(doctorUser.getId())
                .orElseThrow(() -> new ResourceNotFoundException("No doctor profile for this account"));
        // Same doctor-row lock as booking/callNext: two first-saves can't both see "none" and both INSERT
        doctorRepository.findByIdForUpdate(doctor.getId())
                .orElseThrow(() -> new ResourceNotFoundException("No doctor profile for this account"));

        Appointment appointment = ownAppointment(doctor, appointmentId);
        if (!WRITABLE.contains(appointment.getStatus())) {
            throw new ConflictException("A discharge summary can be written only during or after a consultation");
        }

        String notes = request.diagnosisNotes().trim();
        String followUp = blankToNull(request.followUpInstructions());

        Optional<DischargeSummary> existing = summaryRepository.findByAppointmentId(appointmentId);
        if (existing.isEmpty()) {
            DischargeSummary created = summaryRepository.save(DischargeSummary.builder()
                    .appointment(appointment).doctor(doctor)
                    .diagnosisNotes(notes).followUpInstructions(followUp)
                    .build());
            audit(created, doctorUser, DischargeAuditAction.CREATED, null, null);
            return toResponse(created,false);
        }

        DischargeSummary summary = existing.get();
        boolean unchanged = summary.getDiagnosisNotes().equals(notes)
                && Objects.equals(summary.getFollowUpInstructions(), followUp);
        if (unchanged) {
            return toResponse(summary,false);   // re-clicking Save must not flood the audit log
        }
        // Log the OLD values before overwriting them
        audit(summary, doctorUser, DischargeAuditAction.UPDATED,
                summary.getDiagnosisNotes(), summary.getFollowUpInstructions());
        summary.setDiagnosisNotes(notes);
        summary.setFollowUpInstructions(followUp);
        return toResponse(summaryRepository.save(summary),false);
    }

    @Transactional(readOnly = true)
    public DischargeSummaryResponse getForDoctor(User doctorUser, Long appointmentId) {
        Doctor doctor = doctorRepository.findByUserId(doctorUser.getId())
                .orElseThrow(() -> new ResourceNotFoundException("No doctor profile for this account"));
        ownAppointment(doctor, appointmentId);
        return summaryRepository.findByAppointmentId(appointmentId)
                .map(s -> toResponse(s, false))
                .orElseThrow(() -> new ResourceNotFoundException("No discharge summary yet"));
    }

    // NOT readOnly: reading writes an audit row (who saw it, when)
    @Transactional
    public DischargeSummaryResponse getForPatient(User patient, Long appointmentId) {
        Appointment appointment = appointmentRepository.findById(appointmentId)
                .filter(a -> a.getPatient().getId().equals(patient.getId()))
                .orElseThrow(() -> new ResourceNotFoundException("Appointment not found"));
        // While the doctor is still drafting, the patient sees nothing
        if (appointment.getStatus() != AppointmentStatus.COMPLETED) {
            throw new ResourceNotFoundException("No discharge summary yet");
        }
        DischargeSummary summary = summaryRepository.findByAppointmentId(appointmentId)
                .orElseThrow(() -> new ResourceNotFoundException("No discharge summary yet"));
        boolean dietShown = isDietApproved(summary);
        auditRepository.save(DischargeSummaryAudit.builder()
                .summary(summary).actor(patient).action(DischargeAuditAction.VIEWED_BY_PATIENT)
                .shownDietLog(dietShown ? summary.getDietSuggestionLog() : null)
                .build());
        return toResponse(summary, true);
    }

    // Step 1 of 3 (DB only, short): check every rule and build the prompt. No AI call here.
    @Transactional(readOnly = true)
    public DietRequestContext prepareDietPrompt(User doctorUser, Long appointmentId) {
        Doctor doctor = doctorRepository.findByUserId(doctorUser.getId())
                .orElseThrow(() -> new ResourceNotFoundException("No doctor profile for this account"));
        Appointment appointment = ownAppointment(doctor, appointmentId);
        if (!WRITABLE.contains(appointment.getStatus())) {
            throw new ConflictException("A discharge summary can be written only during or after a consultation");
        }
        DischargeSummary summary = summaryRepository.findByAppointmentId(appointmentId)
                .orElseThrow(() -> new ConflictException("Write the discharge summary before generating diet suggestions"));
        // Only DOCTOR-VERIFIED facts may reach the model
        PatientHealthProfile profile = profileRepository.findByPatientId(appointment.getPatient().getId())
                .filter(p -> p.getVerifiedAt() != null)
                .orElseThrow(() -> new ConflictException("Verify the patient's health profile before generating diet suggestions"));
        if (aiLogRepository.countBySummaryId(summary.getId()) >= MAX_DIET_GENERATIONS_PER_VISIT) {
            throw new ConflictException("Diet suggestion limit reached for this visit (" + MAX_DIET_GENERATIONS_PER_VISIT + ")");
        }
        DietPrompt prompt = DietPromptBuilder.build(profile.getConditions(), profile.getFoodAllergies());
        return new DietRequestContext(summary.getId(), prompt);
    }

    // Step 3 of 3 (DB, short): log the attempt; on success attach it and RESET approval.
    @Transactional
    public DischargeSummaryResponse recordDietResult(User doctorUser, DietRequestContext ctx, String model,
                                                     String output, String error, long latencyMs) {
        DischargeSummary summary = summaryRepository.findById(ctx.summaryId())
                .orElseThrow(() -> new ResourceNotFoundException("No discharge summary yet"));
        AiGenerationLog log = aiLogWriter.save(AiGenerationLog.builder()
                .summary(summary).requestedBy(doctorUser).model(model)
                .promptInput(ctx.prompt().asLogText())
                .output(output)
                .status(output != null ? AiGenerationStatus.SUCCESS : AiGenerationStatus.FAILED)
                .errorMessage(error)
                .latencyMs(latencyMs)
                .build());
        if (output == null) {
            return null;   // failure is logged; the caller re-throws
        }
        summary.setDietSuggestionLog(log);
        summary.setDietApprovedBy(null);   // new text = not yet reviewed
        summary.setDietApprovedAt(null);
        audit(summary, doctorUser, DischargeAuditAction.DIET_GENERATED, null, null);
        return toResponse(summaryRepository.save(summary), false);
    }

    @Transactional
    public DischargeSummaryResponse approveDiet(User doctorUser, Long appointmentId, Long generationId) {
        Doctor doctor = doctorRepository.findByUserId(doctorUser.getId())
                .orElseThrow(() -> new ResourceNotFoundException("No doctor profile for this account"));
        // Lock: an approve and a regenerate can't interleave
        doctorRepository.findByIdForUpdate(doctor.getId())
                .orElseThrow(() -> new ResourceNotFoundException("No doctor profile for this account"));
        ownAppointment(doctor, appointmentId);
        DischargeSummary summary = summaryRepository.findByAppointmentId(appointmentId)
                .orElseThrow(() -> new ResourceNotFoundException("No discharge summary yet"));
        AiGenerationLog current = summary.getDietSuggestionLog();
        if (current == null || !current.getId().equals(generationId)) {
            throw new ConflictException("The diet suggestion has changed. Review the latest one before approving");
        }
        summary.setDietApprovedBy(doctorUser);
        summary.setDietApprovedAt(LocalDateTime.now());
        audit(summary, doctorUser, DischargeAuditAction.DIET_APPROVED, null, null);
        return toResponse(summaryRepository.save(summary), false);
    }

    // ---------- helpers ----------

    // Own appointment or 404. "Not yours" and "doesn't exist" look identical.
    private Appointment ownAppointment(Doctor doctor, Long appointmentId) {
        return appointmentRepository.findById(appointmentId)
                .filter(a -> a.getDoctor().getId().equals(doctor.getId()))
                .orElseThrow(() -> new ResourceNotFoundException("Appointment not found"));
    }

    private void audit(DischargeSummary summary, User actor, DischargeAuditAction action,
                       String previousNotes, String previousFollowUp) {
        auditRepository.save(DischargeSummaryAudit.builder()
                .summary(summary).actor(actor).action(action)
                .previousDiagnosisNotes(previousNotes).previousFollowUp(previousFollowUp)
                .build());
    }

    private static String blankToNull(String s) {
        return (s == null || s.isBlank()) ? null : s.trim();
    }

    private static boolean isDietApproved(DischargeSummary s) {
        return s.getDietSuggestionLog() != null && s.getDietApprovedAt() != null;
    }

    // forPatient = true: an UNAPPROVED suggestion is invisible. The disclaimer always travels with the text.
    private DischargeSummaryResponse toResponse(DischargeSummary s, boolean forPatient) {
        AiGenerationLog diet = s.getDietSuggestionLog();
        boolean approved = isDietApproved(s);
        boolean show = diet != null && (!forPatient || approved);
        return new DischargeSummaryResponse(
                s.getAppointment().getId(),
                s.getDoctor().getUser().getName(),
                s.getAppointment().getDepartment(),
                s.getDiagnosisNotes(),
                s.getFollowUpInstructions(),
                s.getCreatedAt(),
                s.getUpdatedAt(),
                show ? diet.getId() : null,
                show ? diet.getOutput() : null,
                approved,
                show ? DIET_DISCLAIMER : null);
    }
}