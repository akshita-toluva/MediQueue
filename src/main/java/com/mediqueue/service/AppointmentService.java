package com.mediqueue.service;

import com.mediqueue.dsaLayer.MinHeap;
import com.mediqueue.dto.AppointmentRequest;
import com.mediqueue.dto.AppointmentResponse;
import com.mediqueue.entity.*;
import com.mediqueue.repository.AppointmentRepository;
import com.mediqueue.repository.DoctorRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AppointmentService {

    private final AppointmentRepository appointmentRepository;
    private final DoctorRepository doctorRepository;

    public AppointmentResponse bookAppointment(AppointmentRequest request, User patient)
    {
        Doctor doctor=doctorRepository.findById(request.getDoctorId())
                .orElseThrow(()->new RuntimeException("Doctor Not Found"));

        if(!doctor.isAvailable())
        {
            throw new RuntimeException("Doctor is not available");
        }

        int queuePosition = appointmentRepository.countByDoctorIdAndStatus(
                doctor.getId(), AppointmentStatus.PENDING) +1;

        Appointment appointment=Appointment.builder()
                .patient(patient)
                .doctor(doctor)
                .department(request.getDepartment())
                .priority(request.getPriority())
                .status(AppointmentStatus.PENDING)
                .queuePosition(queuePosition)
                .build();

        Appointment saved=appointmentRepository.save(appointment);

        recalculateQueue(doctor.getId());

        Appointment refreshed=appointmentRepository.findById(saved.getId())
                .orElseThrow(()->new RuntimeException("Appointment not found after save"));

        return mapToResponse(refreshed);
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
