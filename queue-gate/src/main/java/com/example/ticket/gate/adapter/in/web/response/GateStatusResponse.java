package com.example.ticket.gate.adapter.in.web.response;

import com.example.ticket.gate.application.port.in.StreamQueueInPort.ProgressResult;

public record GateStatusResponse(
        String status,
        long rank,
        long totalInQueue,
        String enterToken,
        String eventId,
        String scheduleId
) {
    public static GateStatusResponse fromResult(ProgressResult result) {
        return new GateStatusResponse(
                result.status().name(),
                result.rank(),
                result.totalInQueue(),
                result.enterToken(),
                result.eventId(),
                result.scheduleId()
        );
    }
}
