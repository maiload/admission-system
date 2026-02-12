package com.example.ticket.core.application.port.in;

import reactor.core.publisher.Flux;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

public interface ActiveScheduleQueryInPort {

    Flux<ActiveScheduleView> findActiveSchedules();

    record ActiveScheduleView(
            UUID eventId,
            UUID scheduleId,
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
