package com.mediqueue.Service;

import com.mediqueue.dto.HealthProfileRequest;
import com.mediqueue.dto.HealthProfileResponse;
import com.mediqueue.entity.*;
import com.mediqueue.exception.ResourceNotFoundException;
import com.mediqueue.repository.AppointmentRepository;
import com.mediqueue.repository.DoctorRepository;
import com.mediqueue.repository.PatientHealthProfileRepository;
import com.mediqueue.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class HealthProfileServiceTest {

    @Mock PatientHealthProfileRepository profileRepository;
    @Mock AppointmentRepository appointmentRepository;
    @Mock DoctorRepository doctorRepository;
    @Mock UserRepository userRepository;

    @InjectMocks HealthProfileService healthProfileService;

    private final User doctorUserA = User.builder().id(1L).role(Role.DOCTOR).build();
    private final Doctor doctorA   = Doctor.builder().id(10L).user(doctorUserA).build();
    private final User patient     = User.builder().id(100L).role(Role.PATIENT).build();
    private final Appointment appointment = Appointment.builder().id(500L).doctor(doctorA).patient(patient).build();

    @Test
    void ownDoctor_patientWithNoProfile_getsEmptyProfile() {
        when(doctorRepository.findByUserId(1L)).thenReturn(Optional.of(doctorA));
        when(appointmentRepository.findById(500L)).thenReturn(Optional.of(appointment));
        when(profileRepository.findByPatientId(100L)).thenReturn(Optional.empty());

        HealthProfileResponse result = healthProfileService.getForDoctor(doctorUserA, 500L);

        assertTrue(result.conditions().isEmpty());
        assertNull(result.updatedAt());          // null = "not filled in yet"
    }


    @Test
    void anotherDoctor_getsNotFound() {
        // Doctor B: a different doctor who has NO appointment with patient 100
        User doctorUserB = User.builder().id(2L).role(Role.DOCTOR).build();
        Doctor doctorB   = Doctor.builder().id(20L).user(doctorUserB).build();

        when(doctorRepository.findByUserId(2L)).thenReturn(Optional.of(doctorB));
        when(appointmentRepository.findById(500L)).thenReturn(Optional.of(appointment)); // belongs to A

        // B asks for A's appointment → 404, the same answer as "doesn't exist"
        assertThrows(ResourceNotFoundException.class,
                () -> healthProfileService.getForDoctor(doctorUserB, 500L));

        // ...and the patient's health data was never even read
        verify(profileRepository, never()).findByPatientId(anyLong());
    }

    @Test
    void verify_whenPatientIsNotInConsultation_isConflict() {
        Appointment waiting = Appointment.builder().id(500L).doctor(doctorA).patient(patient)
                .status(AppointmentStatus.PENDING).build();
        when(doctorRepository.findByUserId(1L)).thenReturn(Optional.of(doctorA));
        when(appointmentRepository.findById(500L)).thenReturn(Optional.of(waiting));

        assertThrows(com.mediqueue.exception.ConflictException.class, () -> healthProfileService.verifyForDoctor(
                doctorUserA, 500L, new HealthProfileRequest(Set.of(), Set.of())));
        verify(profileRepository, never()).save(any());
    }

    @Test
    void verify_byOwnDoctorDuringConsultation_marksVerified() {
        Appointment inRoom = Appointment.builder().id(500L).doctor(doctorA).patient(patient)
                .status(AppointmentStatus.IN_PROGRESS).build();
        when(doctorRepository.findByUserId(1L)).thenReturn(Optional.of(doctorA));
        when(appointmentRepository.findById(500L)).thenReturn(Optional.of(inRoom));
        when(userRepository.findByIdForUpdate(100L)).thenReturn(Optional.of(patient));
        when(profileRepository.findByPatientId(100L)).thenReturn(Optional.empty());
        when(profileRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));   // return what was saved

        HealthProfileResponse result = healthProfileService.verifyForDoctor(doctorUserA, 500L,
                new HealthProfileRequest(Set.of(MedicalCondition.DIABETES), Set.of(FoodAllergy.PEANUT)));

        assertTrue(result.verified());
        assertEquals(Set.of(MedicalCondition.DIABETES), result.conditions());
    }

    @Test
    void patientChangingProfile_removesVerification() {
        PatientHealthProfile existing = verifiedProfileWithDiabetes();
        when(userRepository.findByIdForUpdate(100L)).thenReturn(Optional.of(patient));
        when(profileRepository.findByPatientId(100L)).thenReturn(Optional.of(existing));
        when(profileRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        HealthProfileResponse result = healthProfileService.saveMine(patient,
                new HealthProfileRequest(Set.of(MedicalCondition.DIABETES, MedicalCondition.HYPERTENSION), Set.of()));

        assertFalse(result.verified());
        assertNull(existing.getVerifiedBy());
    }

    @Test
    void patientResavingSameValues_keepsVerification() {
        PatientHealthProfile existing = verifiedProfileWithDiabetes();
        when(userRepository.findByIdForUpdate(100L)).thenReturn(Optional.of(patient));
        when(profileRepository.findByPatientId(100L)).thenReturn(Optional.of(existing));
        when(profileRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        HealthProfileResponse result = healthProfileService.saveMine(patient,
                new HealthProfileRequest(Set.of(MedicalCondition.DIABETES), Set.of()));

        assertTrue(result.verified());
    }

    private PatientHealthProfile verifiedProfileWithDiabetes() {
        PatientHealthProfile p = new PatientHealthProfile();
        p.setPatient(patient);
        p.getConditions().add(MedicalCondition.DIABETES);
        p.setUpdatedAt(LocalDateTime.now().minusDays(1));
        p.setVerifiedBy(doctorUserA);
        p.setVerifiedAt(LocalDateTime.now().minusDays(1));
        return p;
    }
}
