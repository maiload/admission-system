package com.example.ticket.core.application.port.in;

import reactor.core.publisher.Flux;

import java.time.LocalDate;
import java.time.LocalTime;

public interface ActiveScheduleQueryInPort {

    Flux<ActiveScheduleView> findActiveSchedules();

    record ActiveScheduleView(
            String eventId,
            String scheduleId,
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
