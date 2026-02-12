package com.example.ticket.core.application.port.in;

import com.example.ticket.core.application.port.out.SeatQueryPort.SeatView;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.UUID;

public interface SeatQueryInPort {

    Mono<List<ZoneSeatsView>> execute(SeatQuery query);

    record SeatQuery(
            UUID eventId,
            UUID scheduleId,
            String sessionId
    ) {}

    record ZoneSeatsView(
            String zone,
            List<SeatView> seats
    ) {}
}
