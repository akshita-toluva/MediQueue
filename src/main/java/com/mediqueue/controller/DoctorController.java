package com.mediqueue.controller;

import com.mediqueue.Service.AppointmentService;
import com.mediqueue.dto.AppointmentResponse;
import com.mediqueue.dto.DoctorQueueResponse;
import com.mediqueue.entity.User;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;


@RestController
@RequestMapping("/api/doctors/me")
@RequiredArgsConstructor
@PreAuthorize("hasRole('DOCTOR')")
public class DoctorController {

    private final AppointmentService appointmentService;

    @GetMapping("/queue")
    public ResponseEntity<DoctorQueueResponse> myQueue(@AuthenticationPrincipal User currentUser) {
        return ResponseEntity.ok(appointmentService.getMyQueue(currentUser));
    }

    @PostMapping("/call-next")
    public ResponseEntity<AppointmentResponse> callNext(@AuthenticationPrincipal User currentUser) {
        return ResponseEntity.ok(appointmentService.callNext(currentUser));
    }
}