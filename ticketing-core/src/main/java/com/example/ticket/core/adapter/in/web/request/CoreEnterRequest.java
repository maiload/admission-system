package com.example.ticket.core.adapter.in.web.request;

import com.example.ticket.core.application.port.in.EnterCoreInPort.EnterCommand;

public record CoreEnterRequest(
        String eventId,
        String scheduleId
) {
    public EnterCommand toCommand(String enterToken) {
        return new EnterCommand(enterToken, eventId, scheduleId);
    }
}
