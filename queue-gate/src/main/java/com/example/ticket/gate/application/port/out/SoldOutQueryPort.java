package com.example.ticket.gate.application.port.out;

import reactor.core.publisher.Mono;

public interface SoldOutQueryPort {

    /**
     * Check if all seats are sold out for a schedule.
     */
    Mono<Boolean> isSoldOut(String eventId, String scheduleId);
}
