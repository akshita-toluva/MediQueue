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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DischargeSummaryServiceTest {

    @Mock DischargeSummaryRepository summaryRepository;
    @Mock DischargeSummaryAuditRepository auditRepository;
    @Mock AppointmentRepository appointmentRepository;
    @Mock DoctorRepository doctorRepository;
    @InjectMocks DischargeSummaryService service;

    private User doctorUser;
    private Doctor doctor;
    private User patient;
    private Appointment appointment;

    @BeforeEach
    void setUp() {
        doctorUser = User.builder().id(10L).name("Dr Rao").role(Role.DOCTOR).build();
        doctor = Doctor.builder().id(1L).user(doctorUser).department("Cardiology").available(true).avgConsultationTime(15).build();
        patient = User.builder().id(20L).name("Ravi").role(Role.PATIENT).build();
        appointment = Appointment.builder().id(100L).patient(patient).doctor(doctor)
                .department("Cardiology").priority(Priority.NORMAL).build();
    }

    // The three lookups every doctor write does first
    private void stubDoctorLookups() {
        when(doctorRepository.findByUserId(10L)).thenReturn(Optional.of(doctor));
        when(doctorRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(doctor));
        when(appointmentRepository.findById(100L)).thenReturn(Optional.of(appointment));
    }

    private DischargeSummary existingSummary(String notes) {
        return DischargeSummary.builder().id(5L).appointment(appointment).doctor(doctor)
                .diagnosisNotes(notes).build();
    }

    private DischargeSummaryAudit capturedAudit() {
        ArgumentCaptor<DischargeSummaryAudit> captor = ArgumentCaptor.forClass(DischargeSummaryAudit.class);
        verify(auditRepository).save(captor.capture());
        return captor.getValue();
    }

    @Test
    void firstSaveCreatesSummaryAndCreatedAudit() {
        appointment.setStatus(AppointmentStatus.IN_PROGRESS);
        stubDoctorLookups();
        when(summaryRepository.findByAppointmentId(100L)).thenReturn(Optional.empty());
        when(summaryRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        DischargeSummaryResponse r = service.saveForDoctor(doctorUser, 100L,
                new DischargeSummaryRequest("  Viral fever  ", "   "));

        assertEquals("Viral fever", r.diagnosisNotes());
        assertNull(r.followUpInstructions());                 // blank -> null
        assertEquals(DischargeAuditAction.CREATED, capturedAudit().getAction());
    }

    @Test
    void updateKeepsOldTextInAudit() {
        appointment.setStatus(AppointmentStatus.COMPLETED);
        stubDoctorLookups();
        when(summaryRepository.findByAppointmentId(100L)).thenReturn(Optional.of(existingSummary("Old diagnosis")));
        when(summaryRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        DischargeSummaryResponse r = service.saveForDoctor(doctorUser, 100L,
                new DischargeSummaryRequest("New diagnosis", null));

        assertEquals("New diagnosis", r.diagnosisNotes());
        DischargeSummaryAudit audit = capturedAudit();
        assertEquals(DischargeAuditAction.UPDATED, audit.getAction());
        assertEquals("Old diagnosis", audit.getPreviousDiagnosisNotes());
    }

    @Test
    void identicalResaveWritesNoAudit() {
        appointment.setStatus(AppointmentStatus.COMPLETED);
        stubDoctorLookups();
        when(summaryRepository.findByAppointmentId(100L)).thenReturn(Optional.of(existingSummary("Same")));

        service.saveForDoctor(doctorUser, 100L, new DischargeSummaryRequest("Same", null));

        verify(auditRepository, never()).save(any());
        verify(summaryRepository, never()).save(any());
    }

    @Test
    void cannotWriteForNoShow() {
        appointment.setStatus(AppointmentStatus.NO_SHOW);
        stubDoctorLookups();

        assertThrows(ConflictException.class, () -> service.saveForDoctor(doctorUser, 100L,
                new DischargeSummaryRequest("x", null)));
        verify(summaryRepository, never()).save(any());
    }

    @Test
    void anotherDoctorGets404AndNothingIsSaved() {
        appointment.setStatus(AppointmentStatus.IN_PROGRESS);
        Doctor otherDoctor = Doctor.builder().id(2L).user(User.builder().id(11L).build()).build();
        appointment.setDoctor(otherDoctor);
        stubDoctorLookups();

        assertThrows(ResourceNotFoundException.class, () -> service.saveForDoctor(doctorUser, 100L,
                new DischargeSummaryRequest("x", null)));
        verify(summaryRepository, never()).save(any());
    }

    @Test
    void patientSeesNothingWhileConsultationInProgress() {
        appointment.setStatus(AppointmentStatus.IN_PROGRESS);
        when(appointmentRepository.findById(100L)).thenReturn(Optional.of(appointment));

        assertThrows(ResourceNotFoundException.class, () -> service.getForPatient(patient, 100L));
        verify(auditRepository, never()).save(any());
    }

    @Test
    void patientViewIsAudited() {
        appointment.setStatus(AppointmentStatus.COMPLETED);
        when(appointmentRepository.findById(100L)).thenReturn(Optional.of(appointment));
        when(summaryRepository.findByAppointmentId(100L)).thenReturn(Optional.of(existingSummary("Viral fever")));

        DischargeSummaryResponse r = service.getForPatient(patient, 100L);

        assertEquals("Dr Rao", r.doctorName());
        DischargeSummaryAudit audit = capturedAudit();
        assertEquals(DischargeAuditAction.VIEWED_BY_PATIENT, audit.getAction());
        assertEquals(20L, audit.getActor().getId());
    }
}