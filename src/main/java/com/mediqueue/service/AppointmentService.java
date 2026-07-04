package com.mediqueue.service;

import com.mediqueue.dto.AppointmentRequest;
import com.mediqueue.dto.AppointmentResponse;
import com.mediqueue.entity.Appointment;
import com.mediqueue.entity.AppointmentStatus;
import com.mediqueue.entity.Doctor;
import com.mediqueue.entity.User;
import com.mediqueue.repository.AppointmentRepository;
import com.mediqueue.repository.DoctorRepository;
import lombok.AllArgsConstructor;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import javax.print.Doc;
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
        return mapToResponse(saved);
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
