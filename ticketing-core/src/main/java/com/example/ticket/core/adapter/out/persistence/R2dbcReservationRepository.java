package com.example.ticket.core.adapter.out.persistence;

import com.example.ticket.core.application.port.out.ReservationRepositoryPort;
import com.example.ticket.core.domain.reservation.Reservation;
import lombok.RequiredArgsConstructor;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class R2dbcReservationRepository implements ReservationRepositoryPort {

    private final DatabaseClient db;

    @Override
    public Mono<Reservation> save(Reservation reservation) {
        return db.sql("""
                        INSERT INTO reservations (id, schedule_id, seat_id, client_id, confirmed_at)
                        VALUES (:id, :scheduleId, :seatId, :clientId, :confirmedAt)
                        """)
                .bind("id", reservation.getId())
                .bind("scheduleId", reservation.getScheduleId())
                .bind("seatId", reservation.getSeatId())
                .bind("clientId", reservation.getClientId())
                .bind("confirmedAt", reservation.getConfirmedAt())
                .fetch().rowsUpdated()
                .thenReturn(reservation);
    }

    @Override
    public Mono<Reservation> findByScheduleAndSeat(UUID scheduleId, UUID seatId) {
        return db.sql("SELECT * FROM reservations WHERE schedule_id = :scheduleId AND seat_id = :seatId")
                .bind("scheduleId", scheduleId)
                .bind("seatId", seatId)
                .map(row -> new Reservation(
                        row.get("id", UUID.class),
                        row.get("schedule_id", UUID.class),
                        row.get("seat_id", UUID.class),
                        row.get("client_id", String.class),
                        row.get("confirmed_at", Instant.class)
                ))
                .first();
    }

    @Override
    public Mono<Long> countByScheduleAndClient(UUID scheduleId, String clientId) {
        return db.sql("SELECT COUNT(*) AS cnt FROM reservations WHERE schedule_id = :scheduleId AND client_id = :clientId")
                .bind("scheduleId", scheduleId)
                .bind("clientId", clientId)
                .map(row -> row.get("cnt", Long.class))
                .first();
    }
}
