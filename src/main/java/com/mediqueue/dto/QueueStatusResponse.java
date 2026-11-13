package com.mediqueue.dto;


import com.mediqueue.entity.AppointmentStatus;
import lombok.Builder;
import lombok.Data;
import com.mediqueue.entity.Priority;

import java.time.LocalDate;

@Data
@Builder
public class QueueStatusResponse {

    private Long appointmentId;
    private int queuePosition;
    private AppointmentStatus status;
    private int estimatedWaitTime;
    private Integer actualWaitTime;
    private LocalDate date;
    private Priority priority;
}
