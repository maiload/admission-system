package com.example.ticket.admission.application.engine;

import com.example.ticket.admission.application.dto.IssueResult;
import com.example.ticket.admission.application.port.out.ActiveSchedulePort;
import com.example.ticket.admission.application.port.out.IssuerPort;
import com.example.ticket.admission.application.port.out.TokenGeneratorPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * Core admission engine. Each tick processes all active schedules,
 * pops queue members, and issues enter tokens.
 */
@Slf4j
@RequiredArgsConstructor
public class AdmissionEngine {

    private final ActiveSchedulePort activeSchedulePort;
    private final IssuerPort issuerPort;
    private final TokenGeneratorPort tokenGenerator;
    private final int maxBatch;
    private final int rateCap;
    private final int concurrencyCap;
    private final int enterTtlSec;
    private final int qstateTtlSec;
    private final int rateTtlSec;

    /**
     * Single tick: process all active schedules.
     */
    public Mono<Void> tick() {
        return activeSchedulePort.getActiveSchedules()
                .flatMap(this::processSchedule)
                .then();
    }

    private Mono<IssueResult> processSchedule(String scheduleKey) {
        // scheduleKey = "eventId:scheduleId"
        String[] parts = scheduleKey.split(":", 2);
        if (parts.length < 2) {
            return Mono.empty();
        }
        String eventId = parts[0];
        String scheduleId = parts[1];

        // Pre-generate token pairs
        List<String> tokenPairs = tokenGenerator.generateBatch(eventId, scheduleId, maxBatch);

        return issuerPort.issue(eventId, scheduleId,
                        maxBatch, rateCap, concurrencyCap,
                        enterTtlSec, qstateTtlSec, rateTtlSec,
                        tokenPairs)
                .doOnNext(result -> {
                    if (result.getIssued() > 0) {
                        log.info("Schedule {}:{} — issued={}, skipped={}, remaining={}",
                                eventId, scheduleId,
                                result.getIssued(), result.getSkipped(),
                                result.getRemainingQueueSize());
                    }
                })
                .onErrorResume(e -> {
                    log.error("Failed to issue for {}:{}", eventId, scheduleId, e);
                    return Mono.empty();
                });
    }
}
