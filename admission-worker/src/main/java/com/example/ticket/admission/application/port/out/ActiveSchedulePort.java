package com.example.ticket.admission.application.port.out;

import reactor.core.publisher.Flux;

/**
 * Query active schedules from Redis ZSET.
 */
public interface ActiveSchedulePort {

    /**
     * Get active schedule identifiers ("eventId:scheduleId").
     */
    Flux<String> getActiveSchedules();
}
