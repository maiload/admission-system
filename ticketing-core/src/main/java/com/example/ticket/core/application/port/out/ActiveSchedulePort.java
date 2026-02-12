package com.example.ticket.core.application.port.out;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface ActiveSchedulePort {

    Flux<ActiveSchedule> findAll();

    Mono<Boolean> upsert(String eventId, String scheduleId, long startAtMs);

    Mono<Long> clearAll();

    record ActiveSchedule(String eventId, String scheduleId) {}
}
