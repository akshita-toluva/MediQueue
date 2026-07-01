package com.mediqueue.mediqueue.dto;

import com.mediqueue.mediqueue.entity.AppointmentStatus;
import com.mediqueue.mediqueue.entity.Priority;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class AppointmentResponse {
    private Long appointmentId;
    private String patientName;
    private String doctorName;
    private String department;
    private Priority priority;
    private AppointmentStatus status;
    private int queuePosition;
    private LocalDateTime bookedAt;
}
