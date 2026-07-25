package com.mediqueue.repository;

import com.mediqueue.entity.PriorityAuditLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PriorityAuditLogRepository extends JpaRepository<PriorityAuditLog, Long> {
    List<PriorityAuditLog> findByAppointmentIdOrderByChangedAtDesc(Long appointmentId);
}