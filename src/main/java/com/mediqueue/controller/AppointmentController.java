package com.mediqueue.controller;

import com.mediqueue.dto.AppointmentRequest;
import com.mediqueue.dto.AppointmentResponse;
import com.mediqueue.entity.Doctor;
import com.mediqueue.entity.User;
import com.mediqueue.repository.UserRepository;
import com.mediqueue.service.AppointmentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import javax.print.Doc;
import java.util.List;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class AppointmentController {

    private final AppointmentService appointmentService;

    @PostMapping("/appointments/book")
    @PreAuthorize("hasRole('PATIENT')")
    public ResponseEntity<AppointmentResponse> bookAppointment(
            @Valid @RequestBody AppointmentRequest request,
            @AuthenticationPrincipal User currentUser) {

        return ResponseEntity.ok(appointmentService.bookAppointment(request,currentUser));
    }

    @GetMapping("/appointments/my")
    @PreAuthorize("hasRole('PATIENT')")
    public ResponseEntity<List<AppointmentResponse>> getMyAppointments(
            @AuthenticationPrincipal User currentUser){
        return ResponseEntity.ok(appointmentService.getMyAppointments(currentUser));
    }

    @GetMapping("/doctors/available")
    public ResponseEntity<List<Doctor>> getAvailableDoctor(
            @RequestParam(required = false) String department){
        if(department!=null)
        {
            return ResponseEntity.ok(appointmentService.getAvailableDoctorsByDepartment(department));
        }
        return ResponseEntity.ok(appointmentService.getAvailableDoctors());
    }

}
