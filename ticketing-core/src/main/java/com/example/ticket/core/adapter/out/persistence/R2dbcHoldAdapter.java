package com.example.ticket.core.adapter.out.persistence;

import com.example.ticket.core.application.port.out.HoldRepositoryPort;
import com.example.ticket.core.domain.Hold;
import io.r2dbc.spi.Readable;
import lombok.RequiredArgsConstructor;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class R2dbcHoldAdapter implements HoldRepositoryPort {

    private final DatabaseClient db;

    @Override
    public Mono<Hold> save(Hold hold) {
        return db.sql(SqlQueries.HOLD_INSERT)
                .bind("id", hold.id())
                .bind("holdGroupId", hold.holdGroupId())
                .bind("scheduleId", hold.scheduleId())
                .bind("seatId", hold.seatId())
                .bind("clientId", hold.clientId())
                .bind("expiresAt", hold.expiresAt())
                .bind("createdAt", hold.createdAt())
                .fetch().rowsUpdated()
                .thenReturn(hold);
    }

    @Override
    public Mono<Hold> findById(UUID holdId) {
        return db.sql(SqlQueries.HOLD_FIND_BY_ID)
                .bind("id", holdId)
                .map(this::toHold)
                .first();
    }

    @Override
    public Flux<Hold> findByGroupId(UUID holdGroupId) {
        return db.sql(SqlQueries.HOLD_FIND_BY_GROUP_ID)
                .bind("holdGroupId", holdGroupId)
                .map(this::toHold)
                .all();
    }

    @Override
    public Mono<Hold> findByScheduleAndClient(UUID scheduleId, String clientId) {
        return db.sql(SqlQueries.HOLD_FIND_BY_SCHEDULE_AND_CLIENT)
                .bind("scheduleId", scheduleId)
                .bind("clientId", clientId)
                .map(this::toHold)
                .first();
    }

    @Override
    public Mono<Void> deleteById(UUID holdId) {
        return db.sql(SqlQueries.HOLD_DELETE_BY_ID)
                .bind("id", holdId)
                .fetch().rowsUpdated()
                .then();
    }

    @Override
    public Mono<Void> deleteByGroupId(UUID holdGroupId) {
        return db.sql(SqlQueries.HOLD_DELETE_BY_GROUP_ID)
                .bind("holdGroupId", holdGroupId)
                .fetch().rowsUpdated()
                .then();
    }

    @Override
    public Mono<Long> deleteExpired(Instant now) {
        return db.sql(SqlQueries.HOLD_DELETE_EXPIRED)
                .bind("now", now)
                .fetch().rowsUpdated();
    }

    private Hold toHold(Readable row) {
        return new Hold(
                row.get(DbColumns.ID, UUID.class),
                row.get(DbColumns.HOLD_GROUP_ID, UUID.class),
                row.get(DbColumns.SCHEDULE_ID, UUID.class),
                row.get(DbColumns.SEAT_ID, UUID.class),
                row.get(DbColumns.CLIENT_ID, String.class),
                row.get(DbColumns.EXPIRES_AT, Instant.class),
                row.get(DbColumns.CREATED_AT, Instant.class)
        );
    }
}
