package com.example.ticket.core.application.service;

import com.example.ticket.core.application.port.out.SeatQueryPort.SeatView;
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
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SeatQueryService implements SeatQueryInPort {

    private final SeatQueryPort seatQueryPort;
    private final SessionPort sessionPort;

    @Override
    public Mono<List<ZoneSeatsView>> execute(SeatQuery query) {
        return sessionPort.validateSession(toValidateQuery(query))
                .switchIfEmpty(Mono.error(new BusinessException(ErrorCode.SESSION_TOKEN_INVALID)))
                .then(sessionPort.refreshSession(toRefreshCommand(query)))
                .then(seatQueryPort.findAllBySchedule(query.eventId(), query.scheduleId())
                                .collectList()
                                .map(this::groupByZone)
                );
    }

    private SessionPort.ValidateQuery toValidateQuery(SeatQuery query) {
        return new SessionPort.ValidateQuery(
                query.eventId().toString(), query.scheduleId().toString(), query.sessionId()
        );
    }

    private SessionPort.RefreshCommand toRefreshCommand(SeatQuery query) {
        return new SessionPort.RefreshCommand(
                query.eventId().toString(), query.scheduleId().toString(), query.sessionId()
        );
    }

    private List<ZoneSeatsView> groupByZone(List<SeatView> seats) {
        return seats.stream()
                .collect(Collectors.groupingBy(SeatView::zone))
                .entrySet().stream()
                .map(entry -> new ZoneSeatsView(
                        entry.getKey(),
                        entry.getValue().stream()
                                .sorted(Comparator.comparingInt(SeatView::seatNo))
                                .toList()
                ))
                .sorted(Comparator.comparing(ZoneSeatsView::zone))
                .toList();
    }
}
