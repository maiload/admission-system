package com.example.ticket.core.application.port.in;

import reactor.core.publisher.Mono;

import java.util.UUID;

public interface HoldInPort {

    Mono<CreateHoldResult> createHold(CreateHoldCommand command);

    Mono<ConfirmHoldResult> confirmHold(ConfirmHoldCommand command);

    record CreateHoldCommand(
            String clientId,
            UUID scheduleId,
            UUID seatId
    ) {}

    record CreateHoldResult(
            String holdId,
            String scheduleId,
            String seatId,
            int seatNo,
            String zone,
            long expiresAtMs,
            int holdTtlSec
    ) {}

    record ConfirmHoldCommand(
            String clientId,
            UUID holdId,
            String scheduleId,
            String sessionId
    ) {}

    record ConfirmHoldResult(
            UUID reservationId,
            String scheduleId,
            String seatId,
            String zone,
            int seatNo,
            long confirmedAtMs
    ) {}
}
