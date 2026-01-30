package com.example.ticket.core.adapter.out.persistence;

import com.example.ticket.core.application.dto.query.SeatView;
import com.example.ticket.core.application.port.out.SeatQueryPort;
import com.example.ticket.core.domain.seat.SeatStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class R2dbcSeatQuery implements SeatQueryPort {

    private final DatabaseClient db;

    @Override
    public Flux<SeatView> findAllBySchedule(UUID eventId, UUID scheduleId) {
        return db.sql("""
                        SELECT s.id AS seat_id, s.zone, s.seat_no,
                               CASE
                                   WHEN r.id IS NOT NULL THEN 'CONFIRMED'
                                   WHEN h.id IS NOT NULL THEN 'HELD'
                                   ELSE 'AVAILABLE'
                               END AS status
                        FROM seats s
                        LEFT JOIN holds h ON h.seat_id = s.id AND h.schedule_id = :scheduleId
                        LEFT JOIN reservations r ON r.seat_id = s.id AND r.schedule_id = :scheduleId
                        WHERE s.event_id = :eventId
                        ORDER BY s.zone, s.seat_no
                        """)
                .bind("eventId", eventId)
                .bind("scheduleId", scheduleId)
                .map(row -> new SeatView(
                        row.get("seat_id", UUID.class),
                        row.get("zone", String.class),
                        row.get("seat_no", Integer.class),
                        SeatStatus.valueOf(row.get("status", String.class))
                ))
                .all();
    }

    @Override
    public Mono<Long> countTotalSeats(UUID eventId) {
        return db.sql("SELECT COUNT(*) AS cnt FROM seats WHERE event_id = :eventId")
                .bind("eventId", eventId)
                .map(row -> row.get("cnt", Long.class))
                .first();
    }

    @Override
    public Mono<Long> countHeldSeats(UUID scheduleId) {
        return db.sql("SELECT COUNT(*) AS cnt FROM holds WHERE schedule_id = :scheduleId")
                .bind("scheduleId", scheduleId)
                .map(row -> row.get("cnt", Long.class))
                .first();
    }

    @Override
    public Mono<Long> countConfirmedSeats(UUID scheduleId) {
        return db.sql("SELECT COUNT(*) AS cnt FROM reservations WHERE schedule_id = :scheduleId")
                .bind("scheduleId", scheduleId)
                .map(row -> row.get("cnt", Long.class))
                .first();
    }
}
