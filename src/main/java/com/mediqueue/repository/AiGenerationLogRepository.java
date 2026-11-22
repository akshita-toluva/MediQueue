package com.mediqueue.repository;

import com.mediqueue.entity.AiGenerationLog;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AiGenerationLogRepository extends JpaRepository<AiGenerationLog, Long> {
    long countBySummaryId(Long summaryId);
}