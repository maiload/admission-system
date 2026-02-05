package com.example.ticket.core.adapter.out.persistence;

import com.example.ticket.core.application.port.out.HoldRepositoryPort;
import com.example.ticket.core.domain.hold.Hold;
import lombok.RequiredArgsConstructor;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class R2dbcHoldRepository implements HoldRepositoryPort {

    private final DatabaseClient db;

    @Override
    public Mono<Hold> save(Hold hold) {
        return db.sql("""
                        INSERT INTO holds (id, schedule_id, seat_id, client_id, expires_at, created_at)
                        VALUES (:id, :scheduleId, :seatId, :clientId, :expiresAt, :createdAt)
                        """)
                .bind("id", hold.getId())
                .bind("scheduleId", hold.getScheduleId())
                .bind("seatId", hold.getSeatId())
                .bind("clientId", hold.getClientId())
                .bind("expiresAt", hold.getExpiresAt())
                .bind("createdAt", hold.getCreatedAt())
                .fetch().rowsUpdated()
                .thenReturn(hold);
    }

    @Override
    public Mono<Hold> findById(UUID holdId) {
        return db.sql("SELECT * FROM holds WHERE id = :id")
                .bind("id", holdId)
                .map(row -> new Hold(
                        row.get("id", UUID.class),
                        row.get("schedule_id", UUID.class),
                        row.get("seat_id", UUID.class),
                        row.get("client_id", String.class),
                        row.get("expires_at", Instant.class),
                        row.get("created_at", Instant.class)
                ))
                .first();
    }

    @Override
    public Mono<Hold> findByScheduleAndClient(UUID scheduleId, String clientId) {
        return db.sql("SELECT * FROM holds WHERE schedule_id = :scheduleId AND client_id = :clientId")
                .bind("scheduleId", scheduleId)
                .bind("clientId", clientId)
                .map(row -> new Hold(
                        row.get("id", UUID.class),
                        row.get("schedule_id", UUID.class),
                        row.get("seat_id", UUID.class),
                        row.get("client_id", String.class),
                        row.get("expires_at", Instant.class),
                        row.get("created_at", Instant.class)
                ))
                .first();
    }

    @Override
    public Mono<Long> countByScheduleAndClient(UUID scheduleId, String clientId) {
        return db.sql("SELECT COUNT(*) AS cnt FROM holds WHERE schedule_id = :scheduleId AND client_id = :clientId")
                .bind("scheduleId", scheduleId)
                .bind("clientId", clientId)
                .map(row -> row.get("cnt", Long.class))
                .first();
    }

    @Override
    public Mono<Void> deleteById(UUID holdId) {
        return db.sql("DELETE FROM holds WHERE id = :id")
                .bind("id", holdId)
                .fetch().rowsUpdated()
                .then();
    }

    @Override
    public Mono<Long> deleteExpired(Instant now) {
        return db.sql("DELETE FROM holds WHERE expires_at < :now")
                .bind("now", now)
                .fetch().rowsUpdated();
    }
}
