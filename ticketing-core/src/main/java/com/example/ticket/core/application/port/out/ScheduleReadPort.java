package com.example.ticket.core.application.port.out;

import reactor.core.publisher.Flux;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface ScheduleReadPort {

    Flux<ScheduleStartView> findAll();

    Flux<ScheduleDetailView> findDetailsByIds(List<UUID> scheduleIds);

    record ScheduleStartView(
            UUID scheduleId,
            UUID eventId,
            Instant startAt
    ) {}

    record ScheduleDetailView(
            UUID scheduleId,
            UUID eventId,
            Instant startAt,
            String trainName,
            String trainNumber,
            String departure,
            String arrival,
            java.time.LocalTime departureTime,
            java.time.LocalTime arrivalTime,
            java.time.LocalDate serviceDate,
            int price
    ) {}
}
