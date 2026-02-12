package com.example.ticket.core.application.port.in;

import reactor.core.publisher.Mono;

import java.util.List;
import java.util.UUID;

public interface HoldInPort {

    Mono<CreateHoldResult> createHold(CreateHoldCommand command);

    Mono<ConfirmHoldResult> confirmHold(ConfirmHoldCommand command);

    record CreateHoldCommand(
            String clientId,
            UUID eventId,
            UUID scheduleId,
            List<UUID> seatIds
    ) {}

    record HeldSeat(
            UUID seatId,
            int seatNo,
            String zone
    ) {}

    record CreateHoldResult(
            UUID holdGroupId,
            UUID scheduleId,
            List<HeldSeat> seats,
            long expiresAtMs,
            int holdTtlSec
    ) {}

    record ConfirmHoldCommand(
            String clientId,
            UUID holdGroupId,
            UUID eventId,
            UUID scheduleId,
            String sessionId
    ) {}

    record ConfirmedSeat(
            UUID reservationId,
            UUID seatId,
            String zone,
            int seatNo
    ) {}

    record ConfirmHoldResult(
            UUID scheduleId,
            List<ConfirmedSeat> seats,
            long confirmedAtMs
    ) {}
}
