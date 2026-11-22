package com.mediqueue.controller;

import com.mediqueue.Service.AppointmentService;
import com.mediqueue.Service.DietSuggestionService;
import com.mediqueue.Service.DischargeSummaryService;
import com.mediqueue.Service.HealthProfileService;
import com.mediqueue.dto.*;
import com.mediqueue.entity.User;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import com.mediqueue.dto.DischargeSummaryRequest;
import com.mediqueue.dto.DischargeSummaryResponse;
import com.mediqueue.dto.DietApprovalRequest;


@RestController
@RequestMapping("/api/doctors/me")
@RequiredArgsConstructor
@PreAuthorize("hasRole('DOCTOR')")
public class DoctorController {

    private final AppointmentService appointmentService;
    private final HealthProfileService healthProfileService;
    private final DischargeSummaryService dischargeSummaryService;
    private final DietSuggestionService dietSuggestionService;

    @GetMapping("/queue")
    public ResponseEntity<DoctorQueueResponse> myQueue(@AuthenticationPrincipal User currentUser) {
        return ResponseEntity.ok(appointmentService.getMyQueue(currentUser));
    }

    @PostMapping("/call-next")
    public ResponseEntity<AppointmentResponse> callNext(@AuthenticationPrincipal User currentUser) {
        return ResponseEntity.ok(appointmentService.callNext(currentUser));
    }

    @GetMapping("/appointments/{appointmentId}/health-profile")
    public ResponseEntity<HealthProfileResponse> patientHealthProfile(@AuthenticationPrincipal User currentUser,
                                                                      @PathVariable Long appointmentId) {
        return ResponseEntity.ok(healthProfileService.getForDoctor(currentUser, appointmentId));
    }

    @PutMapping("/appointments/{appointmentId}/health-profile")
    public ResponseEntity<HealthProfileResponse> verifyPatientHealthProfile(@AuthenticationPrincipal User currentUser,
                                                                            @PathVariable Long appointmentId,
                                                                            @Valid @RequestBody HealthProfileRequest request) {
        return ResponseEntity.ok(healthProfileService.verifyForDoctor(currentUser, appointmentId, request));
    }

    @PutMapping("/appointments/{appointmentId}/discharge-summary")
    public ResponseEntity<DischargeSummaryResponse> saveDischargeSummary(@AuthenticationPrincipal User currentUser,
                                                                         @PathVariable Long appointmentId,
                                                                         @Valid @RequestBody DischargeSummaryRequest request) {
        return ResponseEntity.ok(dischargeSummaryService.saveForDoctor(currentUser, appointmentId, request));
    }

    @GetMapping("/appointments/{appointmentId}/discharge-summary")
    public ResponseEntity<DischargeSummaryResponse> getDischargeSummary(@AuthenticationPrincipal User currentUser,
                                                                        @PathVariable Long appointmentId) {
        return ResponseEntity.ok(dischargeSummaryService.getForDoctor(currentUser, appointmentId));
    }

    @PostMapping("/appointments/{appointmentId}/discharge-summary/diet-suggestion")
    public ResponseEntity<DischargeSummaryResponse> generateDietSuggestion(@AuthenticationPrincipal User currentUser,
                                                                           @PathVariable Long appointmentId) {
        return ResponseEntity.ok(dietSuggestionService.generate(currentUser, appointmentId));
    }

    @PutMapping("/appointments/{appointmentId}/discharge-summary/diet-suggestion/approval")
    public ResponseEntity<DischargeSummaryResponse> approveDietSuggestion(@AuthenticationPrincipal User currentUser,
                                                                          @PathVariable Long appointmentId,
                                                                          @Valid @RequestBody DietApprovalRequest request) {
        return ResponseEntity.ok(dischargeSummaryService.approveDiet(currentUser, appointmentId, request.generationId()));
    }
}