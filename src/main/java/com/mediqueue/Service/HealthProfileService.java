package com.mediqueue.Service;

import com.mediqueue.dto.HealthProfileRequest;
import com.mediqueue.dto.HealthProfileResponse;
import com.mediqueue.entity.Appointment;
import com.mediqueue.entity.AppointmentStatus;
import com.mediqueue.entity.Doctor;
import com.mediqueue.entity.PatientHealthProfile;
import com.mediqueue.entity.User;
import com.mediqueue.exception.ConflictException;
import com.mediqueue.exception.ResourceNotFoundException;
import com.mediqueue.repository.AppointmentRepository;
import com.mediqueue.repository.DoctorRepository;
import com.mediqueue.repository.PatientHealthProfileRepository;
import com.mediqueue.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class HealthProfileService {

    private final PatientHealthProfileRepository profileRepository;
    private final AppointmentRepository appointmentRepository;
    private final DoctorRepository doctorRepository;
    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public HealthProfileResponse getMine(User patient) {
        return toResponse(profileRepository.findByPatientId(patient.getId()).orElse(null));
    }

    @Transactional
    public HealthProfileResponse saveMine(User patient, HealthProfileRequest request) {
        PatientHealthProfile profile = lockAndLoad(patient.getId());

        // A doctor verified the OLD values. If the patient changes anything, that confirmation
        // no longer applies. Re-saving identical values keeps it.
        boolean changed = !profile.getConditions().equals(request.conditions())
                || !profile.getFoodAllergies().equals(request.foodAllergies());
        if (changed) {
            profile.setVerifiedBy(null);
            profile.setVerifiedAt(null);
        }
        applyValues(profile, request);
        return toResponse(profileRepository.save(profile));
    }

    @Transactional(readOnly = true)
    public HealthProfileResponse getForDoctor(User doctorUser, Long appointmentId) {
        Appointment appointment = ownAppointment(doctorUser, appointmentId);
        return toResponse(profileRepository.findByPatientId(appointment.getPatient().getId()).orElse(null));
    }

    // The doctor confirms (and may correct) the profile of the patient IN THE ROOM.
    @Transactional
    public HealthProfileResponse verifyForDoctor(User doctorUser, Long appointmentId, HealthProfileRequest request) {
        Appointment appointment = ownAppointment(doctorUser, appointmentId);   // another doctor -> same 404
        if (appointment.getStatus() != AppointmentStatus.IN_PROGRESS) {
            throw new ConflictException("You can verify the health profile only of the patient currently in consultation");
        }
        // Same lock as the patient's own save: a patient edit and a doctor verification can't interleave
        PatientHealthProfile profile = lockAndLoad(appointment.getPatient().getId());
        applyValues(profile, request);
        profile.setVerifiedBy(appointment.getDoctor().getUser());
        profile.setVerifiedAt(LocalDateTime.now());
        return toResponse(profileRepository.save(profile));
    }

    // ---------- helpers ----------

    // Own appointment or 404. "Not yours" and "doesn't exist" look identical: no IDOR, no enumeration.
    private Appointment ownAppointment(User doctorUser, Long appointmentId) {
        Doctor doctor = doctorRepository.findByUserId(doctorUser.getId())
                .orElseThrow(() -> new ResourceNotFoundException("No doctor profile for this account"));
        return appointmentRepository.findById(appointmentId)
                .filter(a -> a.getDoctor().getId().equals(doctor.getId()))
                .orElseThrow(() -> new ResourceNotFoundException("Appointment not found"));
    }

    // Lock the patient row FIRST, then find-or-create: two first saves can't both INSERT (Day 20 race fix)
    private PatientHealthProfile lockAndLoad(Long patientId) {
        User lockedPatient = userRepository.findByIdForUpdate(patientId)
                .orElseThrow(() -> new ResourceNotFoundException("Patient not found"));
        return profileRepository.findByPatientId(lockedPatient.getId())
                .orElseGet(() -> {
                    PatientHealthProfile created = new PatientHealthProfile();
                    created.setPatient(lockedPatient);
                    return created;
                });
    }

    // clear + addAll keeps Hibernate's own collection instance (see Day 20 notes)
    private void applyValues(PatientHealthProfile profile, HealthProfileRequest request) {
        profile.getConditions().clear();
        profile.getConditions().addAll(request.conditions());
        profile.getFoodAllergies().clear();
        profile.getFoodAllergies().addAll(request.foodAllergies());
        profile.setUpdatedAt(LocalDateTime.now());
    }

    private HealthProfileResponse toResponse(PatientHealthProfile p) {
        if (p == null) return new HealthProfileResponse(Set.of(), Set.of(), null, false, null, null);
        User verifier = p.getVerifiedBy();
        return new HealthProfileResponse(
                Set.copyOf(p.getConditions()), Set.copyOf(p.getFoodAllergies()), p.getUpdatedAt(),
                verifier != null, p.getVerifiedAt(), verifier == null ? null : verifier.getName());
    }
}