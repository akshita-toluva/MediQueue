package com.mediqueue.Service;

import com.mediqueue.dsaLayer.SlidingWindowNoShowTracker;
import com.mediqueue.entity.Appointment;
import com.mediqueue.entity.AppointmentStatus;
import com.mediqueue.entity.NoShowLog;
import com.mediqueue.repository.AppointmentRepository;
import com.mediqueue.repository.NoShowLogRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class NoShowTrackerService {

    private final SlidingWindowNoShowTracker tracker=new SlidingWindowNoShowTracker();
    private final NoShowLogRepository noShowLogRepository;
    private final AppointmentRepository appointmentRepository;

    // Rebuild the in-memory window from DB on startup - the deque is a live
    // cache, not the source of truth. Without this, every restart resets
    // every doctor's no-show rate to 0, silently corrupting wait estimates
    // until 30 days of fresh data accumulates again.
    @PostConstruct
    public void warmStart()
    {
        LocalDateTime cutoff= LocalDate.now().minusDays(30).atStartOfDay();
        List<Appointment> resolved=appointmentRepository.findByStatusInAndBookedAtAfter
                (List.of(AppointmentStatus.COMPLETED,AppointmentStatus.NO_SHOW),cutoff);

        resolved.stream()
                .sorted((a,b)->a.getBookedAt().compareTo(b.getBookedAt()))
                .forEach(appt->tracker.recordOutcome(
                        appt.getDoctor().getId(),
                        appt.getBookedAt().toLocalDate(),
                        appt.getStatus()==AppointmentStatus.NO_SHOW));
    }

    public void recordNoShow(Appointment appointment)
    {
        Long doctorId= appointment.getDoctor().getId();
        LocalDate today=LocalDate.now();
        tracker.recordOutcome(doctorId,today,true);
        noShowLogRepository.save(NoShowLog.builder()
                .appointment(appointment)
                .patient(appointment.getPatient())
                .doctor(appointment.getDoctor())
                .date(today)
                .build());
    }

    public void recordShow(Long doctorId)
    {
        tracker.recordOutcome(doctorId,LocalDate.now(),false);
    }

    public double getNoShowRate(Long doctorId)
    {
        return tracker.getNoShowRate(doctorId);
    }
}
