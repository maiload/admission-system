package com.example.ticket.gate.application.port.in;

import reactor.core.publisher.Mono;

public interface JoinQueueInPort {

    Mono<JoinResult> execute(Join join);

    record Join(
            String eventId,
            String scheduleId,
            String syncToken,
            String clientId
    ) {}

    record JoinResult(
            String queueToken,
            long estimatedRank,
            String sseUrl,
            boolean alreadyJoined
    ) {}
}
