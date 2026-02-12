package com.example.ticket.gate.adapter.in.web.request;

import com.example.ticket.gate.application.port.in.JoinQueueInPort.Join;

public record JoinRequest(
        String eventId,
        String scheduleId
) {
    public Join toCommand(String clientId, boolean loadTest) {
        return new Join(eventId, scheduleId, clientId, loadTest);
    }
}
