package com.example.ticket.core.application.service;

import com.example.ticket.core.application.port.out.ScheduleReadPort.ScheduleStartView;
import com.example.ticket.core.application.port.in.ActiveScheduleInPort;
import com.example.ticket.core.application.port.out.ActiveScheduleWritePort;
import com.example.ticket.core.application.port.out.ScheduleReadPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
@RequiredArgsConstructor
public class ActivateSchedulesService implements ActiveScheduleInPort {

    private final ScheduleReadPort scheduleReadPort;
    private final ActiveScheduleWritePort activeScheduleWritePort;

    @Override
    public Mono<Long> activateAll() {
        return scheduleReadPort.findAll()
                .flatMap(this::upsert)
                .reduce(0L, Long::sum);
    }

    @Override
    public Mono<Long> clearAll() {
        return activeScheduleWritePort.clearAll();
    }

    private Mono<Long> upsert(ScheduleStartView view) {
        long startAtMs = view.startAt().toEpochMilli();
        return activeScheduleWritePort.upsert(view.eventId().toString(), view.scheduleId().toString(), startAtMs)
                .map(ok -> Boolean.TRUE.equals(ok) ? 1L : 0L);
    }
}
