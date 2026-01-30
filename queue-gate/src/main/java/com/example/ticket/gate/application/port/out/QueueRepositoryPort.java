package com.example.ticket.gate.application.port.out;

import com.example.ticket.gate.application.dto.JoinResult;
import com.example.ticket.gate.application.dto.QueueProgressDto;
import reactor.core.publisher.Mono;

public interface QueueRepositoryPort {

    /**
     * Atomically join the queue (Lua script).
     * Idempotent: returns existing entry if clientId already joined.
     */
    Mono<JoinResult> join(String eventId, String scheduleId, String clientId,
                          String queueToken, double score, long estimatedRank, int ttlSec);

    /**
     * Get current queue state for a queueToken.
     */
    Mono<QueueProgressDto> getState(String eventId, String scheduleId, String queueToken);

    /**
     * Get current queue size.
     */
    Mono<Long> getQueueSize(String eventId, String scheduleId);
}
