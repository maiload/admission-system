package com.example.ticket.core.application.port.out;

import reactor.core.publisher.Flux;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
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
            LocalTime departureTime,
            LocalTime arrivalTime,
            LocalDate serviceDate,
            int price
    ) {}
}
