package com.mediqueue.repository;

import com.mediqueue.entity.Appointment;
import com.mediqueue.entity.AppointmentStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AppointmentRepository extends JpaRepository<Appointment,Long> {
    List<Appointment> findByPatientId(Long patient_id);
    List<Appointment> findByDoctorIdAndStatus(Long doctorId, AppointmentStatus status);
    int countByDoctorIdAndStatus(Long doctorId,AppointmentStatus status);
}
