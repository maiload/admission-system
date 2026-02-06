package com.example.ticket.core.adapter.out.persistence;

import com.example.ticket.core.application.port.out.ScheduleReadPort;
import io.r2dbc.spi.Readable;
import lombok.RequiredArgsConstructor;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;

import java.time.Instant;
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

    private ScheduleStartView toScheduleStartView(Readable row) {
        return new ScheduleStartView(
                row.get(DbColumns.ID, UUID.class),
                row.get(DbColumns.EVENT_ID, UUID.class),
                row.get(DbColumns.START_AT, Instant.class)
        );
    }
}
