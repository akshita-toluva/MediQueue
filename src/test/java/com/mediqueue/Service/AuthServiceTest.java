package com.mediqueue.Service;

import com.mediqueue.Util.JwtUtil;
import com.mediqueue.dsaLayer.DepartmentAvailabilityCache;
import com.mediqueue.dsaLayer.TokenBlocklistService;
import com.mediqueue.dto.RegisterRequest;
import com.mediqueue.entity.Doctor;
import com.mediqueue.entity.Role;
import com.mediqueue.repository.DoctorRepository;
import com.mediqueue.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock UserRepository userRepository;
    @Mock PasswordEncoder passwordEncoder;
    @Mock JwtUtil jwtUtil;
    @Mock AuthenticationManager authenticationManager;
    @Mock DoctorRepository doctorRepository;
    @Mock DepartmentAvailabilityCache departmentAvailabilityCache;
    @Mock TokenBlocklistService tokenBlocklistService;
    @InjectMocks AuthService authService;

    private RegisterRequest doctorRequest(String department) {
        RegisterRequest r = new RegisterRequest();
        r.setName("Dr Test");
        r.setEmail("dr.test@mediqueue.com");
        r.setPassword("Password@123");
        r.setRole(Role.DOCTOR);
        r.setDepartment(department);
        r.setAvgConsultationTime(15);
        return r;
    }

    // Registers a doctor and returns the Doctor object that was handed to save()
    private Doctor registerAndCaptureDoctor(String department) {
        when(userRepository.existsByEmail(anyString())).thenReturn(false);
        when(passwordEncoder.encode(anyString())).thenReturn("hashed");
        when(doctorRepository.findDistinctDepartments()).thenReturn(List.of("Cardiology", "Orthopedics"));

        authService.registerByAdmin(doctorRequest(department), null);

        ArgumentCaptor<Doctor> captor = ArgumentCaptor.forClass(Doctor.class);
        verify(doctorRepository).save(captor.capture());
        return captor.getValue();
    }

    @Test
    void reusesExistingDepartmentSpelling() {
        assertEquals("Cardiology", registerAndCaptureDoctor("  cardiology ").getDepartment());
    }

    @Test
    void newDepartmentIsTrimmedAndSpacesCollapsed() {
        assertEquals("Neurology Dept", registerAndCaptureDoctor("  Neurology   Dept ").getDepartment());
    }

    @Test
    void blankDepartmentIsRejectedBeforeAnythingIsSaved() {
        when(userRepository.existsByEmail(anyString())).thenReturn(false);

        assertThrows(IllegalArgumentException.class,
                () -> authService.registerByAdmin(doctorRequest("   "), null));

        verify(userRepository, never()).save(any());
        verify(doctorRepository, never()).save(any());
    }

    @Test
    void bootstrapRefusedOnceAnAdminExists() {
        org.springframework.test.util.ReflectionTestUtils.setField(authService, "bootstrapSecret", "s3cret");
        org.springframework.test.util.ReflectionTestUtils.setField(authService, "bootstrapOnlyIfNoAdmin", true);
        when(userRepository.existsByRole(Role.ADMIN)).thenReturn(true);

        assertThrows(com.mediqueue.exception.ConflictException.class,
                () -> authService.registerAdminBootstrap(doctorRequest("x"), "s3cret"));
        verify(userRepository, never()).save(any());
    }

    @Test
    void bootstrapDisabledWithoutSecret() {
        org.springframework.test.util.ReflectionTestUtils.setField(authService, "bootstrapSecret", "");

        assertThrows(com.mediqueue.exception.UnauthorizedActionException.class,
                () -> authService.registerAdminBootstrap(doctorRequest("x"), "anything"));
        verify(userRepository, never()).save(any());
    }
}