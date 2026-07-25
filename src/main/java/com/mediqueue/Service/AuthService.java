package com.mediqueue.Service;

import com.mediqueue.dto.AuthResponse;
import com.mediqueue.dto.LoginRequest;
import com.mediqueue.dto.RegisterRequest;
import com.mediqueue.entity.User;
import com.mediqueue.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import com.mediqueue.util.JwtUtil;
import com.mediqueue.entity.Doctor;
import com.mediqueue.entity.Role;
import com.mediqueue.repository.DoctorRepository;
import org.springframework.transaction.annotation.Transactional;


@Service
@RequiredArgsConstructor
public class AuthService {
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final AuthenticationManager authenticationManager;
    private final DoctorRepository doctorRepository;

    @Transactional
    public AuthResponse register(RegisterRequest request)
    {
        if(userRepository.existsByEmail(request.getEmail())){
            throw new RuntimeException("Email already registered");
        }

        User user= User.builder()
                .name(request.getName())
                .email(request.getEmail())
                .password(passwordEncoder.encode(request.getPassword()))
                .role(request.getRole())
                .build();

        userRepository.save(user);

        if(request.getRole()==Role.DOCTOR)
        {
            if(request.getDepartment() == null || request.getAvgConsultationTime() == null)
            {
                throw new RuntimeException("Doctors must provide department and avgConsultationTime");
            }

            Doctor doctor = Doctor.builder()
                    .user(user)
                    .department(request.getDepartment())
                    .available(true)
                    .avgConsultationTime(request.getAvgConsultationTime())
                    .build();
            doctorRepository.save(doctor);
        }
        String token=jwtUtil.generateToken(user);
        return new AuthResponse(token,user.getRole().name(), user.getName());
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
}
