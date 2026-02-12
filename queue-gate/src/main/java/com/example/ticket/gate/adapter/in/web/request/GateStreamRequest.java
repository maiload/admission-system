package com.example.ticket.gate.adapter.in.web.request;

import com.example.ticket.gate.application.port.in.StreamQueueInPort.StreamQuery;

public record GateStreamRequest(
        String queueToken,
        String eventId,
        String scheduleId
) {
    public StreamQuery toQuery() {
        return new StreamQuery(eventId, scheduleId, queueToken);
    }
}
