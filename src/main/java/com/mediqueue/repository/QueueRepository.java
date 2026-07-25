package com.mediqueue.repository;

import com.mediqueue.entity.QueueEntry;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface QueueRepository extends JpaRepository<QueueEntry,Long> {
    Optional<QueueEntry> findByAppointmentId(Long appointmentId);
    List<QueueEntry> findByDoctorIdAndDate(Long doctorId, LocalDate date);
    List<QueueEntry> findTop5ByDoctorIdAndActualWaitTimeIsNotNullOrderByDateDesc(Long doctorId);
}
