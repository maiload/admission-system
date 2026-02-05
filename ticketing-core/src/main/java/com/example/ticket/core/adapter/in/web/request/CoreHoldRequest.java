package com.example.ticket.core.adapter.in.web.request;

import com.example.ticket.core.application.dto.command.CreateHoldCommand;
import java.util.UUID;

public record CoreHoldRequest(
        String eventId,
        String scheduleId,
        String seatId
) {
    public CreateHoldCommand toCommand(String clientId) {
        return new CreateHoldCommand(
                clientId,
                UUID.fromString(scheduleId),
                UUID.fromString(seatId)
        );
    }
}
