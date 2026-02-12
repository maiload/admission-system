package com.example.ticket.core.adapter.in.web.request;

import com.example.ticket.core.application.port.in.EnterCoreInPort.EnterCommand;

import java.util.UUID;

public record CoreEnterRequest(
        String eventId,
        String scheduleId
) {
    public EnterCommand toCommand(String enterToken) {
        return new EnterCommand(
                enterToken,
                UUID.fromString(eventId),
                UUID.fromString(scheduleId)
        );
    }
}
