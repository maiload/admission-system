package com.example.ticket.core.adapter.in.web.request;

import com.example.ticket.core.application.port.in.HoldInPort.CreateHoldCommand;

import java.util.List;
import java.util.UUID;

public record HoldCreateRequest(
        String eventId,
        String scheduleId,
        List<String> seatIds
) {
    public CreateHoldCommand toCommand(String clientId) {
        return new CreateHoldCommand(
                clientId,
                UUID.fromString(eventId),
                UUID.fromString(scheduleId),
                seatIds.stream().map(UUID::fromString).toList()
        );
    }
}
