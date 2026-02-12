package com.example.ticket.core.adapter.out.persistence;

import com.example.ticket.core.application.port.out.ReservationRepositoryPort;
import com.example.ticket.core.domain.Reservation;
import io.r2dbc.spi.Readable;
import lombok.RequiredArgsConstructor;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class R2dbcReservationAdapter implements ReservationRepositoryPort {

    private final DatabaseClient db;

    @Override
    public Mono<Reservation> save(Reservation reservation) {
        return db.sql(SqlQueries.RESERVATION_INSERT)
                .bind("id", reservation.id())
                .bind("scheduleId", reservation.scheduleId())
                .bind("seatId", reservation.seatId())
                .bind("clientId", reservation.clientId())
                .bind("confirmedAt", reservation.confirmedAt())
                .fetch().rowsUpdated()
                .thenReturn(reservation);
    }

    @Override
    public Mono<Reservation> findByScheduleAndSeat(UUID scheduleId, UUID seatId) {
        return db.sql(SqlQueries.RESERVATION_FIND_BY_SCHEDULE_AND_SEAT)
                .bind("scheduleId", scheduleId)
                .bind("seatId", seatId)
                .map(this::toReservation)
                .first();
    }

    private Reservation toReservation(Readable row) {
        return new Reservation(
                row.get(DbColumns.ID, UUID.class),
                row.get(DbColumns.SCHEDULE_ID, UUID.class),
                row.get(DbColumns.SEAT_ID, UUID.class),
                row.get(DbColumns.CLIENT_ID, String.class),
                row.get(DbColumns.CONFIRMED_AT, Instant.class)
        );
    }
}
