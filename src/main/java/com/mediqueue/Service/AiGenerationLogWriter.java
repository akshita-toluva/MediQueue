package com.mediqueue.Service;

import com.mediqueue.entity.AiGenerationLog;
import com.mediqueue.repository.AiGenerationLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

// Separate bean ON PURPOSE: @Transactional only works through Spring's proxy,
// so this can't be a private method inside DischargeSummaryService.
@Service
@RequiredArgsConstructor
public class AiGenerationLogWriter {

    private final AiGenerationLogRepository repository;

    // REQUIRES_NEW = its own transaction, committed immediately.
    // If the caller's transaction later rolls back, the record of the AI call survives.
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public AiGenerationLog save(AiGenerationLog log) {
        return repository.save(log);
    }
}