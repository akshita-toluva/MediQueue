package com.mediqueue.dto;

import com.mediqueue.entity.Role;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@AllArgsConstructor
public class AdminCreatedUserResponse {
    private Long id;
    private String name;
    private String email;
    private Role role;
}
