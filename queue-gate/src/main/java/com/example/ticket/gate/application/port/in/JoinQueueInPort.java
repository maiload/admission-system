package com.example.ticket.gate.application.port.in;

import reactor.core.publisher.Mono;

public interface JoinQueueInPort {

    Mono<JoinResult> execute(Join join);

    record Join(
            String eventId,
            String scheduleId,
            String clientId,
            boolean loadTest
    ) {}

    record JoinResult(
            String queueToken,
            String sseUrl,
            boolean alreadyJoined
    ) {}
}
