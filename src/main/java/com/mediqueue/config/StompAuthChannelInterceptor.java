package com.mediqueue.config;

import com.mediqueue.Service.CustomerUserDetailsService;
import com.mediqueue.Util.JwtUtil;
import com.mediqueue.dsaLayer.TokenBlocklistService;
import com.mediqueue.entity.Appointment;
import com.mediqueue.entity.Role;
import com.mediqueue.entity.User;
import com.mediqueue.repository.AppointmentRepository;
import com.mediqueue.repository.DoctorRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
@RequiredArgsConstructor
public class StompAuthChannelInterceptor implements ChannelInterceptor {

    private static final Pattern APPOINTMENT_TOPIC = Pattern.compile("^/topic/appointment/(\\d{1,18})$");
    private static final Pattern DOCTOR_QUEUE_TOPIC = Pattern.compile("^/topic/doctor/(\\d{1,18})/queue$");

    private final JwtUtil jwtUtil;
    private final CustomerUserDetailsService customerUserDetailsService;
    private final TokenBlocklistService tokenBlocklistService;
    private final AppointmentRepository appointmentRepository;
    private final DoctorRepository doctorRepository;

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null) {
            return message;
        }
        if (StompCommand.CONNECT.equals(accessor.getCommand())) {
            authenticate(accessor);
        }
        if (StompCommand.SUBSCRIBE.equals(accessor.getCommand())) {
            authorizeSubscription(accessor);
        }
        return message;
    }

    private void authenticate(StompHeaderAccessor accessor) {
        String header = firstHeader(accessor, "Authorization");
        if (header == null || !header.startsWith("Bearer ")) {
            throw new AccessDeniedException("Missing bearer token on WebSocket CONNECT");
        }
        String token = header.substring(7);
        if (tokenBlocklistService.isRevoked(token)) {
            throw new AccessDeniedException("Token has been revoked");
        }
        String email = jwtUtil.extractUsername(token);
        User user = (User) customerUserDetailsService.loadUserByUsername(email);
        if (!jwtUtil.isTokenValid(token, user)) {
            throw new AccessDeniedException("Invalid or expired token");
        }
        accessor.getSessionAttributes().put("user", user);
    }

    // Default = DENY. A destination must match one known shape AND pass its owner check.
    private void authorizeSubscription(StompHeaderAccessor accessor) {
        User user = (User) accessor.getSessionAttributes().get("user");
        if (user == null) {
            throw new AccessDeniedException("Not authenticated");
        }
        String destination = accessor.getDestination() == null ? "" : accessor.getDestination();

        Matcher appointmentTopic = APPOINTMENT_TOPIC.matcher(destination);
        if (appointmentTopic.matches()) {
            authorizeAppointment(user, Long.parseLong(appointmentTopic.group(1)));
            return;
        }
        Matcher doctorTopic = DOCTOR_QUEUE_TOPIC.matcher(destination);
        if (doctorTopic.matches()) {
            authorizeDoctorQueue(user, Long.parseLong(doctorTopic.group(1)));
            return;
        }
        throw new AccessDeniedException("Unknown or unsupported topic");
    }

    private void authorizeAppointment(User user, Long appointmentId) {
        Appointment appointment = appointmentRepository.findById(appointmentId)
                .orElseThrow(() -> new AccessDeniedException("Appointment not found"));
        if (user.getRole() == Role.ADMIN) return;
        if (user.getRole() == Role.PATIENT && appointment.getPatient().getId().equals(user.getId())) return;
        if (user.getRole() == Role.DOCTOR && appointment.getDoctor().getUser().getId().equals(user.getId())) return;
        throw new AccessDeniedException("Not authorised to subscribe to this appointment's updates");
    }

    // Only that doctor (or an admin) may watch a doctor's queue
    private void authorizeDoctorQueue(User user, Long doctorId) {
        if (user.getRole() == Role.ADMIN) return;
        boolean ownQueue = user.getRole() == Role.DOCTOR && doctorRepository.findByUserId(user.getId())
                .map(d -> d.getId().equals(doctorId))
                .orElse(false);
        if (!ownQueue) {
            throw new AccessDeniedException("Not authorised to watch this doctor's queue");
        }
    }

    private String firstHeader(StompHeaderAccessor accessor, String name) {
        List<String> values = accessor.getNativeHeader(name);
        return (values == null || values.isEmpty()) ? null : values.get(0);
    }
}