package com.example.ticket.gate.application.port.out;

import reactor.core.publisher.Mono;

/**
 * Query start time of a schedule from Redis active_schedules ZSET.
 */
public interface ScheduleQueryPort {

    /**
     * Get startAtMs for a schedule. Empty if not found/not active.
     */
    Mono<Long> getStartAtMs(String eventId, String scheduleId);
}
