package com.mediqueue.controller;

import com.mediqueue.Service.HealthProfileService;
import com.mediqueue.dto.HealthProfileRequest;
import com.mediqueue.dto.HealthProfileResponse;
import com.mediqueue.entity.User;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

// "/me": the patient id comes from the JWT, never from the URL, so a patient can't read someone else's profile
@RestController
@RequestMapping("/api/patients/me")
@RequiredArgsConstructor
@PreAuthorize("hasRole('PATIENT')")
public class PatientController {

    private final HealthProfileService healthProfileService;

    @GetMapping("/health-profile")
    public ResponseEntity<HealthProfileResponse> getProfile(@AuthenticationPrincipal User currentUser) {
        return ResponseEntity.ok(healthProfileService.getMine(currentUser));
    }

    @PutMapping("/health-profile")
    public ResponseEntity<HealthProfileResponse> saveProfile(@AuthenticationPrincipal User currentUser,
                                                             @Valid @RequestBody HealthProfileRequest request) {
        return ResponseEntity.ok(healthProfileService.saveMine(currentUser, request));
    }
}