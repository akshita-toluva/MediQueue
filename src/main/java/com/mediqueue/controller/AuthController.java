package com.mediqueue.controller;

import com.mediqueue.Service.AuthRateLimitService;
import com.mediqueue.dto.AdminCreatedUserResponse;
import com.mediqueue.dto.AuthResponse;
import com.mediqueue.dto.LoginRequest;
import com.mediqueue.dto.RegisterRequest;
import com.mediqueue.Service.AuthService;
import com.mediqueue.entity.User;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {
    private final AuthService authService;
    private final AuthRateLimitService authRateLimitService;

    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest request)
    {
        return ResponseEntity.ok(authService.register(request));
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request, HttpServletRequest http)
    {
        authRateLimitService.checkLogin(http.getRemoteAddr(), request.getEmail());
        return ResponseEntity.ok(authService.login(request));
    }

    @PostMapping("/bootstrap-admin")
    public ResponseEntity<AuthResponse> bootstrapAdmin(@Valid @RequestBody RegisterRequest request,
                                                       @RequestHeader("X-Bootstrap-Secret") String secret,
                                                       HttpServletRequest http)
    {
        authRateLimitService.checkBootstrap(http.getRemoteAddr());
        return ResponseEntity.ok(authService.registerAdminBootstrap(request,secret));
    }

    @PostMapping("/admin/register")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<AdminCreatedUserResponse> registerByAdmin(
            @Valid @RequestBody RegisterRequest request,
            @AuthenticationPrincipal User currentUser) {
        return ResponseEntity.ok(authService.registerByAdmin(request, currentUser));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@RequestHeader("Authorization") String authHeader) {

        String token = authHeader.substring(7); // strip "Bearer "
        authService.logout(token);
        return ResponseEntity.noContent().build();
    }
}
