package com.example.ticket.core.adapter.in.scheduler;

import com.example.ticket.core.application.port.out.SessionPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class ActiveCleanupScheduler {

    private final SessionPort sessionPort;

    // TODO: iterate active schedules dynamically
    private static final String EVENT_ID = "a0000000-0000-0000-0000-000000000001";
    private static final String SCHEDULE_ID = "b0000000-0000-0000-0000-000000000001";

    @Scheduled(fixedDelayString = "${core.scheduler.active-cleanup-interval-ms}")
    public void cleanup() {
        sessionPort.cleanupExpiredSessions(EVENT_ID, SCHEDULE_ID)
                .subscribe(count -> {
                    if (count > 0) {
                        log.info("Cleaned up {} expired sessions from active set", count);
                    }
                });
    }
}
