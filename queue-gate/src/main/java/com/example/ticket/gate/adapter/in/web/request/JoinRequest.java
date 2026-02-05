package com.example.ticket.gate.adapter.in.web.request;

import com.example.ticket.gate.application.port.in.JoinQueueInPort.Join;

public record JoinRequest(
        String eventId,
        String scheduleId,
        String syncToken
) {
    public Join toCommand(String clientId) {
        return new Join(eventId, scheduleId, syncToken, clientId);
    }
}
