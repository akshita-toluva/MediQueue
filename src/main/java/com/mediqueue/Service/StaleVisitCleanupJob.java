package com.mediqueue.Service;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class StaleVisitCleanupJob {

    private static final Logger log = LoggerFactory.getLogger(StaleVisitCleanupJob.class);
    private final AppointmentService appointmentService;

    // 00:05 every day, hospital time. The zone is explicit so a cloud server running in UTC
    // doesn't close the day at 5:35 am IST.
    @Scheduled(cron = "0 5 0 * * *", zone = "Asia/Kolkata")
    public void nightly() {
        run("nightly");
    }

    // Also once at startup: if the server was down at midnight, yesterday's visits would stay open all day.
    @EventListener(ApplicationReadyEvent.class)
    public void onStartup() {
        run("startup");
    }

    private void run(String trigger) {
        int closed = appointmentService.closeStaleVisits();
        if (closed > 0) {
            log.info("Closed {} stale visit(s) from previous days ({})", closed, trigger);
        }
    }
}