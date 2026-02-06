package com.example.ticket.core.application.port.out;

import reactor.core.publisher.Flux;

import java.time.Instant;
import java.util.UUID;

public interface ScheduleReadPort {

    Flux<ScheduleStartView> findAll();

    record ScheduleStartView(
            UUID scheduleId,
            UUID eventId,
            Instant startAt
    ) {}
}
