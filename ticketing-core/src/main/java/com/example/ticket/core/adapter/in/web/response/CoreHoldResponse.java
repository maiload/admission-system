package com.example.ticket.core.adapter.in.web.response;

import com.example.ticket.core.application.dto.command.HoldResult;

public record CoreHoldResponse(
        String holdId,
        String scheduleId,
        String seatId,
        int seatNo,
        String zone,
        long expiresAtMs,
        int holdTtlSec
) {
    public static CoreHoldResponse fromResult(HoldResult result) {
        return new CoreHoldResponse(
                result.holdId().toString(),
                result.scheduleId(),
                result.seatId().toString(),
                result.seatNo(),
                result.zone(),
                result.expiresAtMs(),
                result.holdTtlSec()
        );
    }
}
