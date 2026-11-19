package com.mediqueue.Service;

import com.mediqueue.dsaLayer.DepartmentAvailabilityCache;
import com.mediqueue.dsaLayer.HashMapDoctorCache;
import com.mediqueue.dto.AppointmentRequest;
import com.mediqueue.entity.*;
import com.mediqueue.exception.ConflictException;
import com.mediqueue.exception.ResourceNotFoundException;
import com.mediqueue.exception.UnauthorizedActionException;
import com.mediqueue.repository.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

// Unit tests for the RULES in AppointmentService: who may do what, and which status moves are allowed.
// Every dependency is a mock, so no database, Valkey or WebSocket is involved.
// Each test stops at a rule check (an exception or a no-op), so the heavy recalculateQueue path never runs.
@ExtendWith(MockitoExtension.class)
class AppointmentServiceTest {

    @Mock AppointmentRepository appointmentRepository;
    @Mock DoctorRepository doctorRepository;
    @Mock QueueRepository queueRepository;
    @Mock WaitTimeEstimationService waitTimeEstimationService;
    @Mock PriorityAuditLogRepository priorityAuditLogRepository;
    @Mock NoShowTrackerService noShowTrackerService;
    @Mock HashMapDoctorCache doctorAvailabilityCache;
    @Mock DepartmentAvailabilityCache departmentAvailabilityCache;
    @Mock QueueBroadcastService queueBroadcastService;

    @InjectMocks AppointmentService appointmentService;

    // ---- shared test data (plain objects, nothing saved) ----
    private final User doctorUserA = User.builder().id(1L).name("Dr A").role(Role.DOCTOR).build();
    private final User doctorUserB = User.builder().id(2L).name("Dr B").role(Role.DOCTOR).build();
    private final Doctor doctorA   = Doctor.builder().id(10L).user(doctorUserA).department("Cardiology").build();
    private final User patient     = User.builder().id(100L).name("Patient").role(Role.PATIENT).build();

    private Appointment appointmentWithStatus(AppointmentStatus status, Priority priority) {
        return Appointment.builder().id(500L).doctor(doctorA).patient(patient)
                .status(status).priority(priority).build();
    }

    private AppointmentRequest bookingRequest(String symptoms) {
        AppointmentRequest r = new AppointmentRequest();
        r.setDoctorId(10L);
        r.setSymptomDescription(symptoms);
        return r;
    }

    // ===================== resolveAppointment: the visit state machine =====================

    @Test
    void resolve_withOutcomeOtherThanCompletedOrNoShow_isRejectedBeforeTouchingTheDatabase() {
        // guards: the first if-check in resolveAppointment
        assertThrows(IllegalArgumentException.class,
                () -> appointmentService.resolveAppointment(500L, AppointmentStatus.CANCELLED, doctorUserA));
        verifyNoInteractions(appointmentRepository, doctorRepository);
    }

    @Test
    void complete_beforeCallNext_isConflict() {
        // guards: COMPLETED only from IN_PROGRESS ("Call the patient in before...")
        when(appointmentRepository.findDoctorIdById(500L)).thenReturn(Optional.of(10L));
        when(appointmentRepository.findById(500L))
                .thenReturn(Optional.of(appointmentWithStatus(AppointmentStatus.PENDING, Priority.NORMAL)));

        assertThrows(ConflictException.class,
                () -> appointmentService.resolveAppointment(500L, AppointmentStatus.COMPLETED, doctorUserA));
        verify(appointmentRepository, never()).save(any());
    }

    @Test
    void noShow_forPatientAlreadyInConsultation_isConflict() {
        // guards: NO_SHOW only from PENDING. Someone in the room can't be a no-show.
        when(appointmentRepository.findDoctorIdById(500L)).thenReturn(Optional.of(10L));
        when(appointmentRepository.findById(500L))
                .thenReturn(Optional.of(appointmentWithStatus(AppointmentStatus.IN_PROGRESS, Priority.NORMAL)));

        assertThrows(ConflictException.class,
                () -> appointmentService.resolveAppointment(500L, AppointmentStatus.NO_SHOW, doctorUserA));
        verify(noShowTrackerService, never()).recordNoShow(any());   // the no-show statistics stay clean
    }

    @Test
    void resolve_byAnotherDoctor_isForbidden() {
        // guards: the ownership check (a doctor can only resolve their own patients)
        when(appointmentRepository.findDoctorIdById(500L)).thenReturn(Optional.of(10L));
        when(appointmentRepository.findById(500L))
                .thenReturn(Optional.of(appointmentWithStatus(AppointmentStatus.IN_PROGRESS, Priority.NORMAL)));

        assertThrows(UnauthorizedActionException.class,
                () -> appointmentService.resolveAppointment(500L, AppointmentStatus.COMPLETED, doctorUserB));
        verify(appointmentRepository, never()).save(any());
    }

    @Test
    void resolve_nonExistentAppointment_isNotFound() {
        when(appointmentRepository.findDoctorIdById(999L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> appointmentService.resolveAppointment(999L, AppointmentStatus.COMPLETED, doctorUserA));
    }

    // ===================== callNext =====================

    @Test
    void callNext_whileSomeoneIsAlreadyInConsultation_isConflict() {
        // guards: one patient in the room at a time
        when(doctorRepository.findByUserId(1L)).thenReturn(Optional.of(doctorA));
        when(appointmentRepository.existsByDoctorIdAndStatus(10L, AppointmentStatus.IN_PROGRESS)).thenReturn(true);

        assertThrows(ConflictException.class, () -> appointmentService.callNext(doctorUserA));
        verify(appointmentRepository, never()).save(any());
    }

    @Test
    void callNext_withNobodyWaiting_isConflict() {
        when(doctorRepository.findByUserId(1L)).thenReturn(Optional.of(doctorA));
        when(appointmentRepository.existsByDoctorIdAndStatus(10L, AppointmentStatus.IN_PROGRESS)).thenReturn(false);
        when(appointmentRepository.findByDoctorIdAndStatusOrderByQueuePositionAsc(10L, AppointmentStatus.PENDING))
                .thenReturn(List.of());

        assertThrows(ConflictException.class, () -> appointmentService.callNext(doctorUserA));
    }

    @Test
    void callNext_alwaysLocksTheDoctorRow() {
        // guards the concurrency design: the lock must be taken before any queue change
        when(doctorRepository.findByUserId(1L)).thenReturn(Optional.of(doctorA));
        when(appointmentRepository.existsByDoctorIdAndStatus(10L, AppointmentStatus.IN_PROGRESS)).thenReturn(true);

        assertThrows(ConflictException.class, () -> appointmentService.callNext(doctorUserA));
        verify(doctorRepository).findByIdForUpdate(10L);
    }

    // ===================== bookAppointment =====================

    @Test
    void book_withUnknownDoctor_isNotFound() {
        when(doctorRepository.findByIdForUpdate(10L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> appointmentService.bookAppointment(bookingRequest("fever"), patient));
    }

    @Test
    void book_withPausedDoctor_isConflictAndNothingIsSaved() {
        when(doctorRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(doctorA));
        when(doctorAvailabilityCache.get(10L)).thenReturn(false);    // "Pause new bookings" is on

        assertThrows(ConflictException.class,
                () -> appointmentService.bookAppointment(bookingRequest("fever"), patient));
        verify(appointmentRepository, never()).save(any());
    }

    @Test
    void book_secondNormalVisitWithSameDoctor_isConflict() {
        // guards: one waiting visit per patient per doctor
        when(doctorRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(doctorA));
        when(doctorAvailabilityCache.get(10L)).thenReturn(true);
        when(waitTimeEstimationService.classifyPriority("fever")).thenReturn(Priority.NORMAL);
        when(appointmentRepository.findFirstByPatientIdAndDoctorIdAndStatus(100L, 10L, AppointmentStatus.PENDING))
                .thenReturn(Optional.of(appointmentWithStatus(AppointmentStatus.PENDING, Priority.NORMAL)));

        assertThrows(ConflictException.class,
                () -> appointmentService.bookAppointment(bookingRequest("fever"), patient));
        verify(appointmentRepository, never()).save(any());
    }

    // ===================== overridePriority =====================

    @Test
    void override_byAnotherDoctor_isForbiddenAndNotAudited() {
        when(appointmentRepository.findDoctorIdById(500L)).thenReturn(Optional.of(10L));
        when(appointmentRepository.findById(500L))
                .thenReturn(Optional.of(appointmentWithStatus(AppointmentStatus.PENDING, Priority.NORMAL)));

        assertThrows(UnauthorizedActionException.class,
                () -> appointmentService.overridePriority(500L, Priority.EMERGENCY, doctorUserB));
        verify(priorityAuditLogRepository, never()).save(any());
    }

    @Test
    void override_toTheSamePriority_isANoOpAndNotAudited() {
        // guards: the audit trail records real changes only, not repeated clicks
        when(appointmentRepository.findDoctorIdById(500L)).thenReturn(Optional.of(10L));
        when(appointmentRepository.findById(500L))
                .thenReturn(Optional.of(appointmentWithStatus(AppointmentStatus.PENDING, Priority.EMERGENCY)));

        appointmentService.overridePriority(500L, Priority.EMERGENCY, doctorUserA);

        verify(priorityAuditLogRepository, never()).save(any());
        verify(appointmentRepository, never()).save(any());
    }
}