package com.example.ticket.core.application.port.out;

import reactor.core.publisher.Mono;

public interface ActiveScheduleWritePort {

    Mono<Boolean> upsert(String eventId, String scheduleId, long startAtMs);

    Mono<Long> clearAll();
}
