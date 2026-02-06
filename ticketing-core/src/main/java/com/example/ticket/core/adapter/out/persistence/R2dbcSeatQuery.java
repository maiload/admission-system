package com.example.ticket.core.adapter.out.persistence;

import com.example.ticket.core.application.port.out.SeatQueryPort;
import com.example.ticket.core.domain.SeatStatus;
import io.r2dbc.spi.Readable;
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
        return db.sql(SqlQueries.SEAT_FIND_ALL_BY_SCHEDULE)
                .bind("eventId", eventId)
                .bind("scheduleId", scheduleId)
                .map(this::toSeatView)
                .all();
    }

    @Override
    public Mono<SeatView> findByScheduleAndSeatId(UUID eventId, UUID scheduleId, UUID seatId) {
        return db.sql(SqlQueries.SEAT_FIND_BY_SCHEDULE_AND_SEAT_ID)
                .bind("eventId", eventId)
                .bind("scheduleId", scheduleId)
                .bind("seatId", seatId)
                .map(this::toSeatView)
                .first();
    }

    @Override
    public Mono<Long> countTotalSeats(UUID eventId) {
        return db.sql(SqlQueries.SEAT_COUNT_TOTAL_BY_EVENT)
                .bind("eventId", eventId)
                .map(row -> row.get(DbColumns.COUNT, Long.class))
                .first();
    }

    @Override
    public Mono<Long> countHeldSeats(UUID scheduleId) {
        return db.sql(SqlQueries.HOLD_COUNT_BY_SCHEDULE)
                .bind("scheduleId", scheduleId)
                .map(row -> row.get(DbColumns.COUNT, Long.class))
                .first();
    }

    @Override
    public Mono<Long> countConfirmedSeats(UUID scheduleId) {
        return db.sql(SqlQueries.RESERVATION_COUNT_BY_SCHEDULE)
                .bind("scheduleId", scheduleId)
                .map(row -> row.get(DbColumns.COUNT, Long.class))
                .first();
    }

    private SeatView toSeatView(Readable row) {
        Integer seatNo = row.get(DbColumns.SEAT_NO, Integer.class);
        if (seatNo == null) {
            throw new IllegalStateException("seat_no is null");
        }
        return new SeatView(
                row.get(DbColumns.SEAT_ID, UUID.class),
                row.get(DbColumns.ZONE, String.class),
                seatNo,
                SeatStatus.valueOf(row.get(DbColumns.STATUS, String.class))
        );
    }
}
