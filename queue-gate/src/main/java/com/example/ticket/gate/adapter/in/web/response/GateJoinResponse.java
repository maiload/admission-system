package com.example.ticket.gate.adapter.in.web.response;

import com.example.ticket.gate.application.port.in.JoinQueueInPort.JoinResult;

public record GateJoinResponse(
        String queueToken,
        String sseUrl,
        boolean alreadyJoined
) {
    public static GateJoinResponse fromResult(JoinResult result) {
        return new GateJoinResponse(
                result.queueToken(),
                result.sseUrl(),
                result.alreadyJoined()
        );
    }
}
