package com.mediqueue.repository;

import com.mediqueue.entity.DischargeSummary;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface DischargeSummaryRepository extends JpaRepository<DischargeSummary, Long> {
    Optional<DischargeSummary> findByAppointmentId(Long appointmentId);
}