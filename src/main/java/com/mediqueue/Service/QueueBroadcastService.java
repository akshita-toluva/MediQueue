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
                appointment.getPriority().name(),
                estimatedWaitTime
        );
        messagingTemplate.convertAndSend("/topic/appointment/" + appointment.getId(), payload);
    }
    // Doctor screen: a SIGNAL only ("your queue changed"), no patient data in the message.
    // The page re-fetches GET /api/doctors/me/queue, which is already authorised.
    public void broadcastDoctorQueueChanged(Long doctorId) {
        messagingTemplate.convertAndSend("/topic/doctor/" + doctorId + "/queue",
                new DoctorQueueChanged(doctorId));
    }

    public record DoctorQueueChanged(Long doctorId) {

    }

    public record QueuePositionMessage(
            Long appointmentId,
            Integer queuePosition,
            String status,
            String priority,
            Integer estimatedWaitTime
    ) {}
}
