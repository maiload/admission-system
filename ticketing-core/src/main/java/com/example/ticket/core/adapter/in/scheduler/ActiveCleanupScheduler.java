package com.example.ticket.core.adapter.in.scheduler;

import com.example.ticket.core.application.port.out.ActiveSchedulePort;
import com.example.ticket.core.application.port.out.SessionPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Slf4j
@Component
@RequiredArgsConstructor
public class ActiveCleanupScheduler {

    private final ActiveSchedulePort activeSchedulePort;
    private final SessionPort sessionPort;

    @Scheduled(fixedDelayString = "${core.scheduler.active-cleanup-interval-ms}")
    public void cleanup() {
        activeSchedulePort.findAll()
                .flatMap(active -> sessionPort.cleanupExpiredSessions(
                        new SessionPort.CleanupQuery(active.eventId(), active.scheduleId())
                ))
                .reduce(0L, Long::sum)
                .doOnNext(total -> {
                    if (total > 0) log.info("Cleaned up {} expired sessions from active set", total);
                })
                .subscribe();
    }
}
