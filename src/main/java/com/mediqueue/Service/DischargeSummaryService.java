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

    private final DischargeSummaryRepository summaryRepository;
    private final DischargeSummaryAuditRepository auditRepository;
    private final AppointmentRepository appointmentRepository;
    private final DoctorRepository doctorRepository;

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
            return toResponse(created);
        }

        DischargeSummary summary = existing.get();
        boolean unchanged = summary.getDiagnosisNotes().equals(notes)
                && Objects.equals(summary.getFollowUpInstructions(), followUp);
        if (unchanged) {
            return toResponse(summary);   // re-clicking Save must not flood the audit log
        }
        // Log the OLD values before overwriting them
        audit(summary, doctorUser, DischargeAuditAction.UPDATED,
                summary.getDiagnosisNotes(), summary.getFollowUpInstructions());
        summary.setDiagnosisNotes(notes);
        summary.setFollowUpInstructions(followUp);
        return toResponse(summaryRepository.save(summary));
    }

    @Transactional(readOnly = true)
    public DischargeSummaryResponse getForDoctor(User doctorUser, Long appointmentId) {
        Doctor doctor = doctorRepository.findByUserId(doctorUser.getId())
                .orElseThrow(() -> new ResourceNotFoundException("No doctor profile for this account"));
        ownAppointment(doctor, appointmentId);
        return summaryRepository.findByAppointmentId(appointmentId)
                .map(this::toResponse)
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
        audit(summary, patient, DischargeAuditAction.VIEWED_BY_PATIENT, null, null);
        return toResponse(summary);
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

    private DischargeSummaryResponse toResponse(DischargeSummary s) {
        return new DischargeSummaryResponse(
                s.getAppointment().getId(),
                s.getDoctor().getUser().getName(),
                s.getAppointment().getDepartment(),
                s.getDiagnosisNotes(),
                s.getFollowUpInstructions(),
                s.getCreatedAt(),
                s.getUpdatedAt());
    }
}