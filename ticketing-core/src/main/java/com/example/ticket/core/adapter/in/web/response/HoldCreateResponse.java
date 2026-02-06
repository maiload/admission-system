package com.example.ticket.core.adapter.in.web.response;

import com.example.ticket.core.application.port.in.HoldInPort.CreateHoldResult;

public record HoldCreateResponse(
        String holdId,
        String scheduleId,
        String seatId,
        int seatNo,
        String zone,
        long expiresAtMs,
    int holdTtlSec
) {
    public static HoldCreateResponse fromResult(CreateHoldResult result) {
        return new HoldCreateResponse(
                result.holdId(),
                result.scheduleId(),
                result.seatId(),
                result.seatNo(),
                result.zone(),
                result.expiresAtMs(),
                result.holdTtlSec()
        );
    }
}
