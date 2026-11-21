package com.mediqueue.dto;

import com.mediqueue.entity.Role;
import jakarta.validation.constraints.*;
import lombok.Data;

@Data
public class RegisterRequest {

    @NotBlank
    private String name;

    @NotBlank
    @Email
    private String email;

    @NotBlank
    @Size(min = 8, max = 72, message = "Password must be 8 to 72 characters")
    private String password;

    @NotNull
    private Role role;

    private String department;

    @Min(value = 1, message = "Average consultation time must be 1 to 120 minutes")
    @Max(value = 120, message = "Average consultation time must be 1 to 120 minutes")
    private Integer avgConsultationTime;

}
