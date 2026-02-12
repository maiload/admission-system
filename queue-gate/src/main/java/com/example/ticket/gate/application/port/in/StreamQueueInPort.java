package com.example.ticket.gate.application.port.in;

import com.example.ticket.gate.domain.QueueStatus;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface StreamQueueInPort {

    Flux<ProgressResult> stream(StreamQuery query);

    Mono<ProgressResult> poll(StreamQuery query);

    record StreamQuery(
            String eventId,
            String scheduleId,
            String queueToken
    ) {}

    record ProgressResult(
            QueueStatus status,
            long rank,
            long totalInQueue,
            String enterToken,
            String eventId,
            String scheduleId
    ) {}
}
