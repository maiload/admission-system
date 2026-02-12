package com.example.ticket.core.application.service;

import com.example.ticket.core.application.port.out.ScheduleReadPort.ScheduleStartView;
import com.example.ticket.core.application.port.in.ActiveScheduleInPort;
import com.example.ticket.core.application.port.out.ActiveSchedulePort;
import com.example.ticket.core.application.port.out.ScheduleReadPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
@RequiredArgsConstructor
public class ActivateSchedulesService implements ActiveScheduleInPort {

    private final ScheduleReadPort scheduleReadPort;
    private final ActiveSchedulePort activeSchedulePort;

    @Override
    public Mono<Long> activateAll() {
        return scheduleReadPort.findAll()
                .flatMap(this::upsert)
                .reduce(0L, Long::sum);
    }

    @Override
    public Mono<Long> clearAll() {
        return activeSchedulePort.clearAll();
    }

    private Mono<Long> upsert(ScheduleStartView view) {
        long startAtMs = view.startAt().toEpochMilli();
        return activeSchedulePort.upsert(view.eventId().toString(), view.scheduleId().toString(), startAtMs)
                .map(ok -> Boolean.TRUE.equals(ok) ? 1L : 0L);
    }
}
