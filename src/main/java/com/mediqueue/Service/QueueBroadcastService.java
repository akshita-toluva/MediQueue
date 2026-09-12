package com.mediqueue.Service;
import com.mediqueue.entity.Appointment;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

@Service
public class QueueBroadcastService {
    private final SimpMessagingTemplate messagingTemplate;
    public QueueBroadcastService(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }
    public void broadcastQueueUpdate(Appointment appointment, Integer estimatedWaitTime) {
        QueuePositionMessage payload = new QueuePositionMessage(
                appointment.getId(),
                appointment.getQueuePosition(),
                appointment.getStatus().name(),
                estimatedWaitTime
        );
        messagingTemplate.convertAndSend("/topic/appointment/" + appointment.getId(), payload);
    }
    public record QueuePositionMessage(
            Long appointmentId,
            Integer queuePosition,
            String status,
            Integer estimatedWaitTime
    ) {}
}
