package com.mediqueue.Service;
import com.mediqueue.dsaLayer.HashMapDoctorCache;
import com.mediqueue.dsaLayer.MinHeap;
import com.mediqueue.dto.*;
import com.mediqueue.entity.*;
import com.mediqueue.exception.ConflictException;
import com.mediqueue.exception.ResourceNotFoundException;
import com.mediqueue.exception.UnauthorizedActionException;
import com.mediqueue.repository.AppointmentRepository;
import com.mediqueue.repository.DoctorRepository;
import com.mediqueue.repository.QueueRepository;
import com.mediqueue.repository.PriorityAuditLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.mediqueue.dsaLayer.DepartmentAvailabilityCache;
import jakarta.annotation.PostConstruct;

import java.time.LocalDate;
import java.util.*;

import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AppointmentService {
    private final AppointmentRepository appointmentRepository;
    private final DoctorRepository doctorRepository;
    private final QueueRepository queueRepository;
    private final WaitTimeEstimationService waitTimeEstimationService;
    private final PriorityAuditLogRepository priorityAuditLogRepository;
    private final NoShowTrackerService noShowTrackerService;
    private final HashMapDoctorCache doctorAvailabilityCache;
    private final DepartmentAvailabilityCache departmentAvailabilityCache;
    private final QueueBroadcastService queueBroadcastService;

    @PostConstruct
    public void warmDepartmentAvailabilityCache() {
        departmentAvailabilityCache.clearAll();
        doctorRepository.findByAvailableTrue()
                .forEach(d -> departmentAvailabilityCache.markAvailable(d.getDepartment(), d.getId()));
    }

    @Transactional
    public AppointmentResponse bookAppointment(AppointmentRequest request, User patient) {
        Doctor doctor = doctorRepository.findByIdForUpdate(request.getDoctorId())
                .orElseThrow(() -> new ResourceNotFoundException("Doctor Not Found"));

        Boolean available = doctorAvailabilityCache.get(doctor.getId());
        if(available==null)
        {
            available = doctor.isAvailable();
            doctorAvailabilityCache.put(doctor.getId(), available);
        }

        if (!available) {
            throw new ConflictException("Doctor is not available");
        }

        Priority priority = waitTimeEstimationService.classifyPriority(request.getSymptomDescription());

        Optional<Appointment> existing = appointmentRepository
                .findFirstByPatientIdAndDoctorIdAndStatus(patient.getId(), doctor.getId(), AppointmentStatus.PENDING);
        if (existing.isPresent()) {
            Appointment current = existing.get();
            // Symptoms got worse while waiting: escalate the visit already in the queue.
            // Blocking a possible emergency is never acceptable; a second queue slot isn't either.
            if (priority == Priority.EMERGENCY && current.getPriority() == Priority.NORMAL) {
                current.setPriority(Priority.EMERGENCY);
                current.setSymptoms(request.getSymptomDescription().trim());   // doctor needs the newer, worse symptoms
                current.setEscalatedAt(LocalDateTime.now());
                priorityAuditLogRepository.save(PriorityAuditLog.builder()
                        .appointment(current)
                        .changedBy(patient)              // audit: escalated by the patient's own triage
                        .oldPriority(Priority.NORMAL)
                        .newPriority(Priority.EMERGENCY)
                        .build());
                recalculateQueue(doctor.getId());
                return mapToResponse(current);
            }
            throw new ConflictException("You already have a visit waiting with this doctor");
        }

        int queuePosition = appointmentRepository.countByDoctorIdAndStatus(
                doctor.getId(), AppointmentStatus.PENDING) + 1;
        Appointment appointment = Appointment.builder()
                .patient(patient)
                .doctor(doctor)
                .department(doctor.getDepartment())
                .priority(priority)
                .status(AppointmentStatus.PENDING)
                .queuePosition(queuePosition)
                .symptoms(request.getSymptomDescription().trim())
                .build();
        Appointment saved = appointmentRepository.save(appointment);
        queueRepository.save(QueueEntry.builder()
                .appointment(saved)
                .doctor(doctor)
                .estimatedWaitTime(0)
                .build());
        recalculateQueue(doctor.getId());
        return mapToResponse(saved);
    }

    @Transactional
    public Doctor updateAvailability(Long doctorId,boolean available,User staff)
    {
        if(staff.getRole()!=Role.DOCTOR && staff.getRole()!= Role.ADMIN)
        {
            throw new UnauthorizedActionException("Only a doctor or admin can change availability");
        }

        Doctor doctor=doctorRepository.findByIdForUpdate(doctorId)
                .orElseThrow(()->new ResourceNotFoundException("Doctor not found"));

        if (staff.getRole() == Role.DOCTOR && !doctor.getUser().getId().equals(staff.getId())) {
            throw new UnauthorizedActionException("You are not authorised to change this doctor's availability");
        }

        doctor.setAvailable(available);
        Doctor saved=doctorRepository.save(doctor);
        doctorAvailabilityCache.put(doctorId,available);

        if(available)
        {
            departmentAvailabilityCache.markAvailable(doctor.getDepartment(),doctorId);
        }
        else
        {
            departmentAvailabilityCache.markUnavailable(doctor.getDepartment(),doctorId);
        }
        return saved;
    }

    @Transactional
    public AppointmentResponse overridePriority(Long appointmentId, Priority newPriority, User changedBy) {
        // Lock first, then read: the appointment can't change between our check and our update.
        Long doctorId = appointmentRepository.findDoctorIdById(appointmentId)
                .orElseThrow(() -> new ResourceNotFoundException("Appointment not found"));
        doctorRepository.findByIdForUpdate(doctorId);
        Appointment appointment = appointmentRepository.findById(appointmentId)
                .orElseThrow(() -> new ResourceNotFoundException("Appointment not found"));

        if (changedBy.getRole() == Role.DOCTOR &&
                !appointment.getDoctor().getUser().getId().equals(changedBy.getId())) {
            throw new UnauthorizedActionException("You are not authorised to modify this appointment");
        }
        Priority oldPriority = appointment.getPriority();
        // Skip logging/recalculating on a no-op (same value re-submitted):
        // the audit trail records real changes, not repeated confirmations.
        if (oldPriority != newPriority) {
            appointment.setPriority(newPriority);
            // Becoming urgent starts the "urgent since" clock; going back to normal clears it,
            // so the patient returns to their booking-time place among normal patients.
            appointment.setEscalatedAt(newPriority == Priority.EMERGENCY ? LocalDateTime.now() : null);
            appointmentRepository.save(appointment);
            priorityAuditLogRepository.save(PriorityAuditLog.builder()
                    .appointment(appointment)
                    .changedBy(changedBy)
                    .oldPriority(oldPriority)
                    .newPriority(newPriority)
                    .build());
            recalculateQueue(doctorId);
        }
        return mapToResponse(appointment);
    }

    public QueueStatusResponse getQueueStatus(Long appointmentId,User requester)
    {
        Appointment appointment=appointmentRepository.findById(appointmentId)
                .orElseThrow(()->new ResourceNotFoundException("Appointment not found"));

        //A patient can only see their own appointment's queue status; doctors/admins can see any.
        if(requester.getRole()==Role.PATIENT && !appointment.getPatient().getId().equals(requester.getId()))
        {
            throw new UnauthorizedActionException("You are not authorised to view this appointment");
        }

        //A patient can only see their own appointment's queue status; a doctor can only see their own patients'.
        if(requester.getRole()==Role.DOCTOR && !appointment.getDoctor().getUser().getId().equals(requester.getId()))
        {
            throw new UnauthorizedActionException("You are not authorised to view this appointment");
        }

        QueueEntry queueEntry=queueRepository.findByAppointmentId(appointmentId)
                .orElseThrow(()->new ResourceNotFoundException("Queue entry not found for this appointment"));

        return QueueStatusResponse.builder()
                .appointmentId(appointment.getId())
                .queuePosition(appointment.getQueuePosition())
                .status(appointment.getStatus())
                .estimatedWaitTime(queueEntry.getEstimatedWaitTime())
                .actualWaitTime(queueEntry.getActualWaitTime())
                .date(queueEntry.getDate())
                .priority(appointment.getPriority())
                .build();

    }

    @Transactional
    public AppointmentResponse resolveAppointment(Long appointmentId, AppointmentStatus outcome, User staff) {
        if (outcome != AppointmentStatus.COMPLETED && outcome != AppointmentStatus.NO_SHOW) {
            throw new IllegalArgumentException("Outcome must be COMPLETED or NO_SHOW");
        }
        // Lock the doctor row FIRST, then load the appointment, so its status can't change
        // (e.g. a parallel "Call next") between our check and our update.
        Long doctorId = appointmentRepository.findDoctorIdById(appointmentId)
                .orElseThrow(() -> new ResourceNotFoundException("Appointment not found"));
        doctorRepository.findByIdForUpdate(doctorId);
        Appointment appointment = appointmentRepository.findById(appointmentId)
                .orElseThrow(() -> new ResourceNotFoundException("Appointment not found"));

        if (staff.getRole() == Role.DOCTOR &&
                !appointment.getDoctor().getUser().getId().equals(staff.getId())) {
            throw new UnauthorizedActionException("You are not authorised to resolve this appointment");
        }
        // Allowed moves only: PENDING -> IN_PROGRESS -> COMPLETED, or PENDING -> NO_SHOW
        if (outcome == AppointmentStatus.COMPLETED && appointment.getStatus() != AppointmentStatus.IN_PROGRESS) {
            throw new ConflictException("Call the patient in before marking the visit completed");
        }
        if (outcome == AppointmentStatus.NO_SHOW && appointment.getStatus() != AppointmentStatus.PENDING) {
            throw new ConflictException("Only a waiting patient can be marked as a no-show");
        }

        appointment.setStatus(outcome);
        if (outcome == AppointmentStatus.NO_SHOW) {
            noShowTrackerService.recordNoShow(appointment);
        } else {
            appointment.setConsultationEndedAt(LocalDateTime.now());
            noShowTrackerService.recordShow(doctorId);
            // Real wait = booked -> called in. The old value (booked -> finished) wrongly included the consultation.
            queueRepository.findByAppointmentId(appointmentId).ifPresent(qe -> {
                qe.setActualWaitTime((int) Duration.between(
                        appointment.getBookedAt(), appointment.getConsultationStartedAt()).toMinutes());
                queueRepository.save(qe);
            });
        }
        appointmentRepository.save(appointment);
        recalculateQueue(doctorId);
        broadcastAfterCommit(appointment);
        return mapToResponse(appointment);
    }

    @Transactional
    public int closeStaleVisits() {
        LocalDateTime startOfToday = LocalDate.now().atStartOfDay();
        List<Appointment> stale = appointmentRepository
                .findByStatusAndBookedAtBefore(AppointmentStatus.PENDING, startOfToday);
        List<Appointment> forgotten = appointmentRepository
                .findByStatusAndBookedAtBefore(AppointmentStatus.IN_PROGRESS, startOfToday);
        if (stale.isEmpty() && forgotten.isEmpty()) return 0;

        Set<Long> doctorIds = new HashSet<>();
        stale.forEach(a -> { a.setStatus(AppointmentStatus.CANCELLED); doctorIds.add(a.getDoctor().getId()); });
        // Seen, but the end time is unknown: consultationEndedAt stays null, so Day 19's
        // duration history ignores it instead of learning a fake 14-hour consultation.
        forgotten.forEach(a -> { a.setStatus(AppointmentStatus.COMPLETED); doctorIds.add(a.getDoctor().getId()); });
        appointmentRepository.saveAll(stale);
        appointmentRepository.saveAll(forgotten);

        for (Long doctorId : doctorIds) {
            doctorRepository.findByIdForUpdate(doctorId);
            recalculateQueue(doctorId);
        }
        stale.forEach(this::broadcastAfterCommit);
        forgotten.forEach(this::broadcastAfterCommit);
        return stale.size() + forgotten.size();
    }

    private List<Integer> recentConsultationMinutes(Long doctorId) {
        return appointmentRepository
                .findTop10ByDoctorIdAndStatusAndConsultationEndedAtIsNotNullOrderByConsultationEndedAtDesc(
                        doctorId, AppointmentStatus.COMPLETED)
                .stream()
                .filter(a -> a.getConsultationStartedAt() != null)
                .map(a -> (int) Duration.between(a.getConsultationStartedAt(), a.getConsultationEndedAt()).toMinutes())
                .toList();
    }
    private void recalculateQueue(Long doctorId) {
        List<Appointment> pending =
                appointmentRepository.findByDoctorIdAndStatus(doctorId, AppointmentStatus.PENDING);
        Comparator<Appointment> queueOrder = Comparator
                .comparing((Appointment a) -> a.getPriority() == Priority.EMERGENCY ? 0 : 1)
                .thenComparing(a -> a.getPriority() == Priority.EMERGENCY && a.getEscalatedAt() != null
                        ? a.getEscalatedAt()
                        : a.getBookedAt());
        MinHeap<Appointment> heap = new MinHeap<>(queueOrder);
        pending.forEach(heap::insert);
        int position = 1;
        List<Appointment> ordered = new ArrayList<>();
        while (!heap.isEmpty()) {
            Appointment next = heap.extractMin();
            next.setQueuePosition(position++);
            ordered.add(next);
        }
        appointmentRepository.saveAll(ordered);
        // BEFORE the early return: the doctor's screen must refresh even when the queue just became empty
        afterCommit(() -> queueBroadcastService.broadcastDoctorQueueChanged(doctorId));
        if (ordered.isEmpty()) return;

        // Positions changed, so every wait estimate must change with them.
        // Fetch history + no-show rate ONCE per recalculation, not once per patient.
        Doctor doctor = ordered.get(0).getDoctor();
        List<Integer> recent = recentConsultationMinutes(doctorId);
        double noShowRate = noShowTrackerService.getNoShowRate(doctorId);

        int inProgress = appointmentRepository.existsByDoctorIdAndStatus(doctorId, AppointmentStatus.IN_PROGRESS) ? 1 : 0;

        List<Long> orderedIds = ordered.stream().map(Appointment::getId).toList();
        Map<Long, QueueEntry> entryByAppointmentId = queueRepository.findByAppointmentIdIn(orderedIds)
                .stream()
                .collect(Collectors.toMap(qe -> qe.getAppointment().getId(), qe -> qe));

        for (Appointment a : ordered) {
            QueueEntry qe = entryByAppointmentId.get(a.getId());
            if (qe != null) {
                qe.setEstimatedWaitTime(waitTimeEstimationService.predictWaitTime(
                        a.getQueuePosition() - 1+ inProgress, doctor.getAvgConsultationTime(), recent, noShowRate));
            }
        }
        queueRepository.saveAll(entryByAppointmentId.values());

        Map<Long, Integer> waitTimeByAppointmentId = entryByAppointmentId.entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue().getEstimatedWaitTime()));

        Runnable broadcast = () -> {
            for (Appointment appointment : ordered) {
                queueBroadcastService.broadcastQueueUpdate(
                        appointment, waitTimeByAppointmentId.get(appointment.getId()));
            }
        };

        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    broadcast.run();
                }
            });
        } else {
            broadcast.run();
        }
    }

    public List<AppointmentResponse> getMyAppointments(User patient) {
        return appointmentRepository.findByPatientIdOrderByBookedAtDesc(patient.getId())
                .stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    public List<DoctorSummaryResponse> getAvailableDoctors() {
        List<Long> ids = departmentAvailabilityCache.getAllAvailableDoctorIds();
        return doctorRepository.findAllById(ids).stream().map(this::toSummary).toList();
    }

    public List<DoctorSummaryResponse> getAvailableDoctorsByDepartment(String department) {
        List<Long> ids = departmentAvailabilityCache.getAvailableDoctorIds(department);
        return doctorRepository.findAllById(ids).stream().map(this::toSummary).toList();
    }

    private DoctorSummaryResponse toSummary(Doctor d) {
        return new DoctorSummaryResponse(d.getId(), d.getUser().getName(), d.getDepartment(), d.getAvgConsultationTime());
    }

    private AppointmentResponse mapToResponse(Appointment appointment) {
        return AppointmentResponse.builder()
                .appointmentId(appointment.getId())
                .patientName(appointment.getPatient().getName())
                .doctorName(appointment.getDoctor().getUser().getName())
                .department(appointment.getDepartment())
                .priority(appointment.getPriority())
                .status(appointment.getStatus())
                .queuePosition(appointment.getQueuePosition())
                .bookedAt(appointment.getBookedAt())
                .build();
    }

    private Doctor doctorFor(User user) {
        return doctorRepository.findByUserId(user.getId())
                .orElseThrow(() -> new ResourceNotFoundException("No doctor profile for this account"));
    }

    private DoctorQueueItem toQueueItem(Appointment a) {
        return new DoctorQueueItem(a.getId(), a.getPatient().getName(), a.getSymptoms(),
                a.getPriority(), a.getQueuePosition(), a.getBookedAt(), a.getConsultationStartedAt());
    }

    private void broadcastAfterCommit(Appointment appointment) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                queueBroadcastService.broadcastQueueUpdate(appointment, null);
            }
        });
    }

    @Transactional(readOnly = true)
    public DoctorQueueResponse getMyQueue(User doctorUser) {
        Doctor doctor = doctorFor(doctorUser);
        DoctorQueueItem current = appointmentRepository
                .findFirstByDoctorIdAndStatus(doctor.getId(), AppointmentStatus.IN_PROGRESS)
                .map(this::toQueueItem).orElse(null);
        List<DoctorQueueItem> waiting = appointmentRepository
                .findByDoctorIdAndStatusOrderByQueuePositionAsc(doctor.getId(), AppointmentStatus.PENDING)
                .stream().map(this::toQueueItem).toList();
        return new DoctorQueueResponse(doctor.getId(), doctor.isAvailable(), current, waiting);
    }

    // The SERVER picks who is next (top of the queue), not the browser:
    // two tabs or a double-click can never start two different patients.
    @Transactional
    public AppointmentResponse callNext(User doctorUser) {
        Doctor doctor = doctorFor(doctorUser);
        doctorRepository.findByIdForUpdate(doctor.getId());   // same lock as booking: one queue change at a time

        if (appointmentRepository.existsByDoctorIdAndStatus(doctor.getId(), AppointmentStatus.IN_PROGRESS)) {
            throw new ConflictException("Finish the current consultation before calling the next patient");
        }
        Appointment next = appointmentRepository
                .findByDoctorIdAndStatusOrderByQueuePositionAsc(doctor.getId(), AppointmentStatus.PENDING)
                .stream().findFirst()
                .orElseThrow(() -> new ConflictException("No patients waiting"));

        next.setStatus(AppointmentStatus.IN_PROGRESS);
        next.setConsultationStartedAt(LocalDateTime.now());
        appointmentRepository.save(next);
        recalculateQueue(doctor.getId());   // everyone behind moves up one
        broadcastAfterCommit(next);         // the called patient's card learns "With the doctor"
        return mapToResponse(next);
    }
    // Send only after the DB commit: a rolled-back change must never reach a screen
    private void afterCommit(Runnable action) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() { action.run(); }
            });
        } else {
            action.run();
        }
    }
}