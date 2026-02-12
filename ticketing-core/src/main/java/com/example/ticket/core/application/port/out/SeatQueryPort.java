package com.example.ticket.core.application.port.out;

import com.example.ticket.core.domain.SeatStatus;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

public interface SeatQueryPort {

    Flux<SeatView> findAllBySchedule(UUID eventId, UUID scheduleId);

    Mono<SeatView> findByScheduleAndSeatId(UUID eventId, UUID scheduleId, UUID seatId);

    Mono<Long> countTotalSeats(UUID eventId);

    Mono<Long> countHeldSeats(UUID scheduleId);

    Mono<Long> countConfirmedSeats(UUID scheduleId);

    record SeatView(
            UUID seatId,
            String zone,
            int seatNo,
            SeatStatus status
    ) {}
}
