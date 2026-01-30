package com.example.ticket.gate.application.usecase;

import com.example.ticket.common.port.ClockPort;
import com.example.ticket.gate.application.dto.QueueProgressDto;
import com.example.ticket.gate.application.port.out.QueueRepositoryPort;
import com.example.ticket.gate.application.port.out.SoldOutQueryPort;
import com.example.ticket.gate.domain.QueueStatus;
import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;

@RequiredArgsConstructor
public class StreamQueueUseCase {

    private final QueueRepositoryPort queueRepository;
    private final SoldOutQueryPort soldOutQuery;
    private final ClockPort clock;
    private final int pushIntervalMs;

    /**
     * Create an SSE stream that pushes queue progress every pushIntervalMs.
     * Terminates on ADMISSION_GRANTED, SOLD_OUT, or EXPIRED.
     */
    public Flux<QueueProgressDto> stream(String eventId, String scheduleId, String queueToken) {
        return Flux.interval(Duration.ZERO, Duration.ofMillis(pushIntervalMs))
                .flatMap(tick -> buildProgress(eventId, scheduleId, queueToken))
                .takeUntil(dto -> dto.getStatus() != QueueStatus.WAITING);
    }

    /**
     * Single poll for status (fallback for SSE).
     */
    public Mono<QueueProgressDto> poll(String eventId, String scheduleId, String queueToken) {
        return buildProgress(eventId, scheduleId, queueToken);
    }

    private Mono<QueueProgressDto> buildProgress(String eventId, String scheduleId, String queueToken) {
        Mono<QueueProgressDto> stateMono = queueRepository.getState(eventId, scheduleId, queueToken);
        Mono<Long> sizeMono = queueRepository.getQueueSize(eventId, scheduleId);
        Mono<Boolean> soldOutMono = soldOutQuery.isSoldOut(eventId, scheduleId);

        return Mono.zip(stateMono, sizeMono, soldOutMono)
                .map(tuple -> {
                    QueueProgressDto state = tuple.getT1();
                    long totalInQueue = tuple.getT2();
                    boolean soldOut = tuple.getT3();

                    QueueStatus status = state.getStatus();
                    if (soldOut && status == QueueStatus.WAITING) {
                        status = QueueStatus.SOLD_OUT;
                    }

                    return new QueueProgressDto(
                            status,
                            state.getEstimatedRank(),
                            totalInQueue,
                            state.getEnterToken(),
                            eventId,
                            scheduleId,
                            clock.nowMillis()
                    );
                })
                .defaultIfEmpty(new QueueProgressDto(
                        QueueStatus.EXPIRED, 0, 0, null,
                        eventId, scheduleId, clock.nowMillis()
                ));
    }
}
