package com.example.ticket.core.adapter.out.persistence;

import com.example.ticket.core.application.port.out.ScheduleReadPort;
import io.r2dbc.spi.Readable;
import lombok.RequiredArgsConstructor;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class R2dbcScheduleQuery implements ScheduleReadPort {

    private final DatabaseClient db;

    @Override
    public Flux<ScheduleStartView> findAll() {
        return db.sql(SqlQueries.SCHEDULE_FIND_ALL)
                .map(this::toScheduleStartView)
                .all();
    }

    @Override
    public Flux<ScheduleDetailView> findDetailsByIds(List<UUID> scheduleIds) {
        if (scheduleIds.isEmpty()) {
            return Flux.empty();
        }
        return db.sql(SqlQueries.SCHEDULE_FIND_DETAILS_BY_IDS)
                .bind("scheduleIds", scheduleIds.toArray(new UUID[0]))
                .map(this::toScheduleDetailView)
                .all();
    }

    private ScheduleStartView toScheduleStartView(Readable row) {
        return new ScheduleStartView(
                row.get(DbColumns.ID, UUID.class),
                row.get(DbColumns.EVENT_ID, UUID.class),
                row.get(DbColumns.START_AT, Instant.class)
        );
    }

    private ScheduleDetailView toScheduleDetailView(Readable row) {
        return new ScheduleDetailView(
                row.get(DbColumns.ID, UUID.class),
                row.get(DbColumns.EVENT_ID, UUID.class),
                row.get(DbColumns.START_AT, Instant.class),
                row.get(DbColumns.TRAIN_NAME, String.class),
                row.get(DbColumns.TRAIN_NUMBER, String.class),
                row.get(DbColumns.DEPARTURE, String.class),
                row.get(DbColumns.ARRIVAL, String.class),
                row.get(DbColumns.DEPARTURE_TIME, LocalTime.class),
                row.get(DbColumns.ARRIVAL_TIME, LocalTime.class),
                row.get(DbColumns.SERVICE_DATE, LocalDate.class),
                row.get(DbColumns.PRICE, Integer.class)
        );
    }
}
