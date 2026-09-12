package com.mediqueue.Service;

import com.mediqueue.dsaLayer.TokenBlocklistService;
import com.mediqueue.dto.AdminCreatedUserResponse;
import com.mediqueue.dto.AuthResponse;
import com.mediqueue.dto.LoginRequest;
import com.mediqueue.dto.RegisterRequest;
import com.mediqueue.entity.User;
import com.mediqueue.exception.UnauthorizedActionException;
import com.mediqueue.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import com.mediqueue.Util.JwtUtil;
import com.mediqueue.entity.Doctor;
import com.mediqueue.entity.Role;
import com.mediqueue.repository.DoctorRepository;
import org.springframework.transaction.annotation.Transactional;
import com.mediqueue.dsaLayer.DepartmentAvailabilityCache;
import com.mediqueue.exception.ConflictException;


@Service
@RequiredArgsConstructor
public class AuthService {
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final AuthenticationManager authenticationManager;
    private final DoctorRepository doctorRepository;
    private final DepartmentAvailabilityCache departmentAvailabilityCache;
    private final TokenBlocklistService tokenBlocklistService;
    @Value("${mediqueue.admin.bootstrap-secret}")
    private String bootstrapSecret;

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        if (request.getRole() == Role.ADMIN || request.getRole() == Role.DOCTOR) {
            throw new UnauthorizedActionException("ADMIN and DOCTOR accounts cannot be self-registered. Use /api/auth/admin/register.");
        }
        User user = createUserRecord(request, request.getRole());
        String token = jwtUtil.generateToken(user);
        return new AuthResponse(token, user.getRole().name(), user.getName());
    }


    @Transactional
    public AuthResponse registerAdminBootstrap(RegisterRequest request, String providedSecret) {
        if (!bootstrapSecret.equals(providedSecret)) {
            throw new UnauthorizedActionException("Invalid bootstrap secret");
        }
        User user = createUserRecord(request, Role.ADMIN);
        String token = jwtUtil.generateToken(user);
        return new AuthResponse(token, user.getRole().name(), user.getName());
    }

    public AuthResponse login(LoginRequest request)
    {
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                        request.getEmail(),request.getPassword())
        );

        User user=(User) userRepository.findByEmail(request.getEmail())
                .orElseThrow(()->new RuntimeException("User not found"));

        String token=jwtUtil.generateToken(user);
        return new AuthResponse(token,user.getRole().name(), user.getName());
    }

    public void logout(String token)
    {
        long remaining = jwtUtil.getRemainingValidityMillis(token);
        tokenBlocklistService.revoke(token, remaining);
    }

    @Transactional
    public AdminCreatedUserResponse registerByAdmin(RegisterRequest request, User actingAdmin) {
        User user = createUserRecord(request, request.getRole());
        return AdminCreatedUserResponse.builder()
                .id(user.getId()).name(user.getName())
                .email(user.getEmail()).role(user.getRole())
                .build();
    }

    private User createUserRecord(RegisterRequest request, Role role) {
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new ConflictException("Email already registered");
        }
        User user = User.builder()
                .name(request.getName())
                .email(request.getEmail())
                .password(passwordEncoder.encode(request.getPassword()))
                .role(role)
                .build();
        userRepository.save(user);

        if (role == Role.DOCTOR) {
            if (request.getDepartment() == null || request.getAvgConsultationTime() == null) {
                throw new IllegalArgumentException("Doctors must provide department and avgConsultationTime");
            }
            Doctor doctor = Doctor.builder()
                    .user(user)
                    .department(request.getDepartment())
                    .available(true)
                    .avgConsultationTime(request.getAvgConsultationTime())
                    .build();
            doctorRepository.save(doctor);
            departmentAvailabilityCache.markAvailable(doctor.getDepartment(), doctor.getId());
        }
        return user;
    }
}
