package com.mediqueue.repository;

import com.mediqueue.entity.DischargeSummaryAudit;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DischargeSummaryAuditRepository extends JpaRepository<DischargeSummaryAudit, Long> {
    List<DischargeSummaryAudit> findBySummaryIdOrderByOccurredAtAsc(Long summaryId);
}