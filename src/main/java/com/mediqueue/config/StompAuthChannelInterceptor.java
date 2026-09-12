package com.mediqueue.config;

import com.mediqueue.Service.CustomerUserDetailsService;
import com.mediqueue.Util.JwtUtil;
import com.mediqueue.dsaLayer.TokenBlocklistService;
import com.mediqueue.entity.Appointment;
import com.mediqueue.entity.Role;
import com.mediqueue.entity.User;
import com.mediqueue.repository.AppointmentRepository;
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

@Component
@RequiredArgsConstructor
public class StompAuthChannelInterceptor implements ChannelInterceptor {

    private static final String APPOINTMENT_TOPIC_PREFIX = "/topic/appointment/";

    private final JwtUtil jwtUtil;
    private final CustomerUserDetailsService customerUserDetailsService;
    private final TokenBlocklistService tokenBlocklistService;
    private final AppointmentRepository appointmentRepository;

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message,
                StompHeaderAccessor.class);
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

    private void authenticate(StompHeaderAccessor accessor)
    {
        String header= firstHeader(accessor,"Authorization");
        if(header==null || !header.startsWith("Bearer "))
        {
            throw new AccessDeniedException("Missing bearer token on WebSocket CONNECT");
        }
        String token=header.substring(7);

        if(tokenBlocklistService.isRevoked(token))
        {
            throw new AccessDeniedException("Token has been revoked");
        }

        String email=jwtUtil.extractUsername(token);
        User user=(User)customerUserDetailsService.loadUserByUsername(email);
        if(!jwtUtil.isTokenValid(token,user))
        {
            throw new AccessDeniedException("Invalid or expired token");
        }

        accessor.getSessionAttributes().put("user",user);
    }

    private void authorizeSubscription(StompHeaderAccessor accessor)
    {
        String destination = accessor.getDestination();
        if (destination == null || !destination.startsWith(APPOINTMENT_TOPIC_PREFIX))
        {
            throw new AccessDeniedException("Unknown or unsupported topic");
        }
        User user = (User) accessor.getSessionAttributes().get("user");
        if (user == null)
        {
            throw new AccessDeniedException("Not authenticated");
        }
        Long appointmentId = Long.parseLong(destination.substring(APPOINTMENT_TOPIC_PREFIX.length()));
        Appointment appointment = appointmentRepository.findById(appointmentId)
                .orElseThrow(() -> new AccessDeniedException("Appointment not found"));
        if (user.getRole() == Role.ADMIN) {
            return;
        }
        if (user.getRole() == Role.PATIENT && appointment.getPatient().getId().equals(user.getId()))
        {
            return;
        }
        if (user.getRole() == Role.DOCTOR &&
                appointment.getDoctor().getUser().getId().equals(user.getId())) {
            return;
        }
        throw new AccessDeniedException("Not authorised to subscribe to this appointment's updates");
    }

    private String firstHeader(StompHeaderAccessor accessor, String name)
    {
        List<String> values = accessor.getNativeHeader(name);
        return (values == null || values.isEmpty()) ? null : values.get(0);
    }
}
