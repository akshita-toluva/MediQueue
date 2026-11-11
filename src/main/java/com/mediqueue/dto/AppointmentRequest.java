package com.mediqueue.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class AppointmentRequest {

    @NotNull(message="Doctor_id should not be null")
    private Long doctorId;

    /*@NotBlank(message = "Department is required")
    private String department;*/

    @NotBlank(message = "Please describe your symptoms")
    @Size(max = 500)
    private String symptomDescription;
}
