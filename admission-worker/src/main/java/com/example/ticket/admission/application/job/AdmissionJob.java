package com.example.ticket.admission.application.job;

import com.example.ticket.admission.application.dto.IssueResult;
import com.example.ticket.admission.application.port.out.ActiveSchedulePort;
import com.example.ticket.admission.application.port.out.IssuerPort;
import com.example.ticket.admission.application.port.out.TokenGeneratorPort;
import com.example.ticket.admission.domain.AdmissionConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

import java.util.List;

@Slf4j
@RequiredArgsConstructor
public class AdmissionJob {

    private final ActiveSchedulePort activeSchedulePort;
    private final IssuerPort issuerPort;
    private final TokenGeneratorPort tokenGeneratorPort;
    private final AdmissionConfig config;

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

        return issuerPort.isHeadReady(eventId, scheduleId, config)
                .filter(ready -> ready)
                .flatMap(ready -> {
                    List<String> tokenPairs = tokenGeneratorPort.generateBatch(eventId, scheduleId, config.maxBatch());
                    return issuerPort.issue(eventId, scheduleId, config, tokenPairs);
                })
                .filter(result -> result.issued() > 0)
                .doOnNext(result ->
                        log.info("Schedule {}:{} — issued={}, skipped={}, remaining={}",
                                eventId, scheduleId,
                                result.issued(), result.skipped(),
                                result.remainingQueueSize())
                )
                .onErrorResume(e -> {
                    log.error("Failed to issue for {}:{}", eventId, scheduleId, e);
                    return Mono.empty();
                });
    }
}
