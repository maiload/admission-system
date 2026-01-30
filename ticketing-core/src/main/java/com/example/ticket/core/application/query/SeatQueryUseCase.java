package com.example.ticket.core.application.query;

import com.example.ticket.core.application.dto.query.SeatView;
import com.example.ticket.core.application.dto.query.ZoneSeatsView;
import com.example.ticket.core.application.port.out.SeatQueryPort;
import com.example.ticket.core.application.port.out.SessionPort;
import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Mono;

import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@RequiredArgsConstructor
public class SeatQueryUseCase {

    private final SeatQueryPort seatQueryPort;
    private final SessionPort sessionPort;

    public Mono<List<ZoneSeatsView>> execute(String sessionId, String eventId, UUID scheduleId) {
        // 1. Refresh session TTL
        return sessionPort.refreshSession(eventId, scheduleId.toString(), sessionId)
                .then(
                        // 2. Query seats with status
                        seatQueryPort.findAllBySchedule(UUID.fromString(eventId), scheduleId)
                                .collectList()
                                .map(this::groupByZone)
                );
    }

    private List<ZoneSeatsView> groupByZone(List<SeatView> seats) {
        return seats.stream()
                .collect(Collectors.groupingBy(SeatView::getZone))
                .entrySet().stream()
                .map(entry -> new ZoneSeatsView(
                        entry.getKey(),
                        entry.getValue().stream()
                                .sorted(Comparator.comparingInt(SeatView::getSeatNo))
                                .collect(Collectors.toList())
                ))
                .sorted(Comparator.comparing(ZoneSeatsView::getZone))
                .collect(Collectors.toList());
    }
}
