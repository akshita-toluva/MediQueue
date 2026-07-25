package com.mediqueue.dto;

import com.mediqueue.entity.Priority;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class AppointmentRequest {

    @NotNull(message="Doctor_id should not be null")
    private Long doctorId;

    @NotBlank(message = "Department is required")
    private String department;

    @NotBlank(message = "Please describe your symptoms")
    private String symptomDescription;
}
