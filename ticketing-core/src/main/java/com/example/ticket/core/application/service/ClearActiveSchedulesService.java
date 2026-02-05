package com.example.ticket.core.application.service;

import com.example.ticket.core.application.port.in.ClearActiveSchedulesInPort;
import com.example.ticket.core.application.port.out.ActiveScheduleWritePort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
@RequiredArgsConstructor
public class ClearActiveSchedulesService implements ClearActiveSchedulesInPort {

    private final ActiveScheduleWritePort activeScheduleWritePort;

    @Override
    public Mono<Long> clearAll() {
        return activeScheduleWritePort.clearAll();
    }
}
