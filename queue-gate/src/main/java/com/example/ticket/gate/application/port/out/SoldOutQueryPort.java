package com.example.ticket.gate.application.port.out;

import reactor.core.publisher.Mono;

public interface SoldOutQueryPort {

    Mono<Boolean> isSoldOut(Query query);

    record Query(String eventId, String scheduleId) {}
}
