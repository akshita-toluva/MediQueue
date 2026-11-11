package com.mediqueue.dto;

import com.mediqueue.entity.Priority;
import java.time.LocalDateTime;

public record DoctorQueueItem(
        Long appointmentId,
        String patientName,
        String symptoms,
        Priority priority,
        int queuePosition,
        LocalDateTime bookedAt,
        LocalDateTime consultationStartedAt) {

}