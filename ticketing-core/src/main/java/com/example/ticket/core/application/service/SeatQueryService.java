package com.example.ticket.core.application.service;

import com.example.ticket.core.application.dto.query.SeatQuery;
import com.example.ticket.core.application.dto.query.SeatView;
import com.example.ticket.core.application.dto.query.ZoneSeatsView;
import com.example.ticket.core.application.port.in.SeatQueryInPort;
import com.example.ticket.core.application.port.out.SeatQueryPort;
import com.example.ticket.core.application.port.out.SessionPort;
import com.example.ticket.common.error.BusinessException;
import com.example.ticket.common.error.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SeatQueryService implements SeatQueryInPort {

    private final SeatQueryPort seatQueryPort;
    private final SessionPort sessionPort;

    @Override
    public Mono<List<ZoneSeatsView>> execute(SeatQuery query) {
        // 1. Validate session
        return sessionPort.validateSession(query.eventId(), query.scheduleId().toString(), query.sessionId())
                .switchIfEmpty(Mono.error(new BusinessException(ErrorCode.SESSION_TOKEN_INVALID)))
                .then(
                        // 2. Refresh session TTL
                        sessionPort.refreshSession(query.eventId(), query.scheduleId().toString(), query.sessionId())
                )
                .then(
                        // 3. Query seats with status
                        seatQueryPort.findAllBySchedule(UUID.fromString(query.eventId()), query.scheduleId())
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
