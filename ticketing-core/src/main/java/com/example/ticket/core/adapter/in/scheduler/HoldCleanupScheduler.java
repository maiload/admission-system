package com.example.ticket.core.adapter.in.scheduler;

import com.example.ticket.common.port.ClockPort;
import com.example.ticket.core.application.port.out.HoldRepositoryPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class HoldCleanupScheduler {

    private final HoldRepositoryPort holdRepositoryPort;
    private final ClockPort clockPort;

    @Scheduled(fixedDelayString = "${core.scheduler.hold-cleanup-interval-ms}")
    public void cleanup() {
        holdRepositoryPort.deleteExpired(clockPort.now())
                .doOnNext(count -> {
                    if (count > 0) log.info("Cleaned up {} expired holds", count);
                })
                .subscribe();
    }
}
