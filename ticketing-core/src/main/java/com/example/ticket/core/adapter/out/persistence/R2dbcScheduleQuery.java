package com.example.ticket.core.adapter.out.persistence;

import com.example.ticket.core.application.dto.schedule.ScheduleStartView;
import com.example.ticket.core.application.port.out.ScheduleReadPort;
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
        return db.sql("SELECT id, event_id, start_at FROM schedules")
                .map(row -> new ScheduleStartView(
                        row.get("id", UUID.class),
                        row.get("event_id", UUID.class),
                        row.get("start_at", Instant.class)
                ))
                .all();
    }
}
