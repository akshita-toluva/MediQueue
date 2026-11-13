package com.mediqueue.Service;

import com.mediqueue.dto.HealthProfileRequest;
import com.mediqueue.dto.HealthProfileResponse;
import com.mediqueue.entity.Appointment;
import com.mediqueue.entity.Doctor;
import com.mediqueue.entity.PatientHealthProfile;
import com.mediqueue.entity.User;
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
        // Lock first, then read (same pattern as the doctor row in AppointmentService).
        // Without it, two simultaneous first saves both see "no profile", both INSERT,
        // and the unique constraint turns the loser into a 500.
        User lockedPatient = userRepository.findByIdForUpdate(patient.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Patient not found"));

        PatientHealthProfile profile = profileRepository.findByPatientId(lockedPatient.getId())
                .orElseGet(() -> {
                    PatientHealthProfile created = new PatientHealthProfile();
                    created.setPatient(lockedPatient);
                    return created;
                });

        // clear + addAll, not setX(newSet): Hibernate tracks THIS collection instance;
        // swapping in a new one forces a full delete/re-insert and can confuse dirty-checking.
        profile.getConditions().clear();
        profile.getConditions().addAll(request.conditions());
        profile.getFoodAllergies().clear();
        profile.getFoodAllergies().addAll(request.foodAllergies());
        profile.setUpdatedAt(LocalDateTime.now());

        return toResponse(profileRepository.save(profile));
    }

    // A doctor sees a patient's profile only through an appointment with that doctor.
    // Someone else's appointment and a non-existent one both get the same 404, so a doctor
    // can't probe ids to learn which appointments exist (no IDOR, no enumeration).
    @Transactional(readOnly = true)
    public HealthProfileResponse getForDoctor(User doctorUser, Long appointmentId) {
        Doctor doctor = doctorRepository.findByUserId(doctorUser.getId())
                .orElseThrow(() -> new ResourceNotFoundException("No doctor profile for this account"));

        Appointment appointment = appointmentRepository.findById(appointmentId)
                .filter(a -> a.getDoctor().getId().equals(doctor.getId()))
                .orElseThrow(() -> new ResourceNotFoundException("Appointment not found"));

        return toResponse(profileRepository.findByPatientId(appointment.getPatient().getId()).orElse(null));
    }

    private HealthProfileResponse toResponse(PatientHealthProfile p) {
        if (p == null) return new HealthProfileResponse(Set.of(), Set.of(), null);
        // Set.copyOf: hand out a plain immutable copy, not Hibernate's live collection
        return new HealthProfileResponse(Set.copyOf(p.getConditions()),
                Set.copyOf(p.getFoodAllergies()), p.getUpdatedAt());
    }
}