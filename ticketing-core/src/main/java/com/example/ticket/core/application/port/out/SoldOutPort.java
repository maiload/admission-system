package com.example.ticket.core.application.port.out;

import reactor.core.publisher.Mono;

public interface SoldOutPort {

    Mono<Boolean> isSoldOut(String eventId, String scheduleId);

    Mono<Void> markSoldOut(String eventId, String scheduleId, long ttlSeconds);
}
