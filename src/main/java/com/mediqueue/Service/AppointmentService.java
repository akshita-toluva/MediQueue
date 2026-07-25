package com.mediqueue.Service;

import com.mediqueue.dsaLayer.MinHeap;
import com.mediqueue.dto.AppointmentRequest;
import com.mediqueue.dto.AppointmentResponse;
import com.mediqueue.entity.*;
import com.mediqueue.repository.AppointmentRepository;
import com.mediqueue.repository.DoctorRepository;
import com.mediqueue.repository.QueueRepository;
import com.mediqueue.repository.PriorityAuditLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;


import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AppointmentService {

    private final AppointmentRepository appointmentRepository;
    private final DoctorRepository doctorRepository;
    private final QueueRepository queueRepository;
    private final WaitTimeEstimationService waitTimeEstimationService;
    private final PriorityAuditLogRepository priorityAuditLogRepository;

    @Transactional
    public AppointmentResponse bookAppointment(AppointmentRequest request, User patient)
    {
        Doctor doctor=doctorRepository.findByIdForUpdate(request.getDoctorId())
                .orElseThrow(()->new RuntimeException("Doctor Not Found"));

        if(!doctor.isAvailable())
        {
            throw new RuntimeException("Doctor is not available");
        }

        Priority priority = waitTimeEstimationService.classifyPriority(request.getSymptomDescription());

        int queuePosition = appointmentRepository.countByDoctorIdAndStatus(
                doctor.getId(), AppointmentStatus.PENDING) +1;

        Appointment appointment=Appointment.builder()
                .patient(patient)
                .doctor(doctor)
                .department(request.getDepartment())
                .priority(priority)
                .status(AppointmentStatus.PENDING)
                .queuePosition(queuePosition)
                .build();

        Appointment saved=appointmentRepository.save(appointment);

        recalculateQueue(doctor.getId());

        Appointment refreshed=appointmentRepository.findById(saved.getId())
                .orElseThrow(()->new RuntimeException("Appointment not found after save"));

        int patientsAhead = refreshed.getQueuePosition() - 1;
        List<Integer> recentActualWaitTimes = recentActualWaitTimes(doctor.getId());
        int estimatedWaitTime = waitTimeEstimationService.predictWaitTime(patientsAhead, doctor.getAvgConsultationTime(), recentActualWaitTimes);

        QueueEntry queueEntry = QueueEntry.builder()
                .appointment(refreshed)
                .doctor(doctor)
                .estimatedWaitTime(estimatedWaitTime)
                .build();
        queueRepository.save(queueEntry);

        return mapToResponse(refreshed);
    }

    @Transactional
    public AppointmentResponse overridePriority(Long appointmentId, Priority newPriority, User changedBy) {
        Appointment appointment = appointmentRepository.findById(appointmentId)
                .orElseThrow(() -> new RuntimeException("Appointment not found"));

        doctorRepository.findByIdForUpdate(appointment.getDoctor().getId())
                .orElseThrow(() -> new RuntimeException("Doctor not found"));

        Priority oldPriority = appointment.getPriority();

        // Skip logging/recalculating on a no-op PATCH (same value re-submitted) -
        // an audit trail should record actual changes, not repeated confirmations
        if (oldPriority != newPriority) {
            appointment.setPriority(newPriority);
            appointmentRepository.save(appointment);

            priorityAuditLogRepository.save(
                    PriorityAuditLog.builder()
                            .appointment(appointment)
                            .changedBy(changedBy)
                            .oldPriority(oldPriority)
                            .newPriority(newPriority)
                            .build()
            );

            recalculateQueue(appointment.getDoctor().getId());
        }

        Appointment refreshed = appointmentRepository.findById(appointmentId)
                .orElseThrow(() -> new RuntimeException("Appointment not found after override"));

        return mapToResponse(refreshed);
    }

    private List<Integer> recentActualWaitTimes(Long doctorId) {
        return queueRepository.findTop5ByDoctorIdAndActualWaitTimeIsNotNullOrderByDateDesc(doctorId)
                .stream()
                .map(QueueEntry::getActualWaitTime)
                .collect(Collectors.toList());
    }

    private void recalculateQueue(Long doctorId) {
        List<Appointment> pending=appointmentRepository.findByDoctorIdAndStatus(doctorId,AppointmentStatus.PENDING);

        Comparator<Appointment> queueOrder=Comparator
                .comparing((Appointment a ) ->a.getPriority() == Priority.EMERGENCY ? 0 : 1)
                .thenComparing(Appointment :: getBookedAt);

        MinHeap<Appointment> heap=new MinHeap<>(queueOrder);
        pending.forEach(heap::insert);

        int position=1;
        List<Appointment> ordered=new ArrayList<>();
        while(!heap.isEmpty())
        {
            Appointment next=heap.extractMin();
            next.setQueuePosition(position++);
            ordered.add(next);
        }
        appointmentRepository.saveAll(ordered);
    }

    public List<AppointmentResponse> getMyAppointments(User patient)
    {
        return appointmentRepository.findByPatientId(patient.getId())
                .stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    public List<Doctor> getAvailableDoctors()
    {
        return doctorRepository.findByAvailableTrue();
    }

    public List<Doctor> getAvailableDoctorsByDepartment(String department)
    {
        return doctorRepository.findByDepartmentAndAvailableTrue(department);
    }

    private AppointmentResponse mapToResponse(Appointment appointment)
    {
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
}