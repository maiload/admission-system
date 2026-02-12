package com.example.ticket.gate.application.port.out;

import com.example.ticket.gate.application.port.in.JoinQueueInPort.JoinResult;
import com.example.ticket.gate.application.port.in.StreamQueueInPort.ProgressResult;
import reactor.core.publisher.Mono;

public interface QueueRepositoryPort {

    Mono<JoinResult> join(JoinCommand command);

    Mono<ProgressResult> getState(StateQuery query);

    Mono<Long> getQueueSize(SizeQuery query);

    Mono<Void> refreshStateTtl(StateTtlCommand command);

    record JoinCommand(
            String eventId,
            String scheduleId,
            String clientId,
            String queueToken,
            int stateTtlSec,
            boolean loadTest
    ) {}

    record StateQuery(String eventId, String scheduleId, String queueToken) {}

    record SizeQuery(String eventId, String scheduleId) {}

    record StateTtlCommand(
            String eventId,
            String scheduleId,
            String queueToken,
            int ttlSec,
            int refreshThresholdSec
    ) {}
}
