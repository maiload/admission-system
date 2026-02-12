package com.example.ticket.core.application.port.out;

import com.example.ticket.core.domain.Reservation;
import reactor.core.publisher.Mono;

import java.util.UUID;

public interface ReservationRepositoryPort {

    Mono<Reservation> save(Reservation reservation);

    Mono<Reservation> findByScheduleAndSeat(UUID scheduleId, UUID seatId);

}
