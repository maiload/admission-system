package com.example.ticket.gate.application.port.out;

import reactor.core.publisher.Mono;

public interface ScheduleQueryPort {

    Mono<Long> getStartAtMs(Query query);

    record Query(String eventId, String scheduleId) {}
}
