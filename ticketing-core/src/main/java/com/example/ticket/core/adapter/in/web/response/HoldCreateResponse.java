package com.example.ticket.core.adapter.in.web.response;

import com.example.ticket.core.application.port.in.HoldInPort.CreateHoldResult;

import java.util.List;

public record HoldCreateResponse(
        String holdGroupId,
        String scheduleId,
        List<HeldSeatResponse> seats,
        long expiresAtMs,
        int holdTtlSec
) {
    public record HeldSeatResponse(
            String seatId,
            int seatNo,
            String zone
    ) {}

    public static HoldCreateResponse fromResult(CreateHoldResult result) {
        List<HeldSeatResponse> seats = result.seats().stream()
                .map(s -> new HeldSeatResponse(
                        s.seatId().toString(),
                        s.seatNo(),
                        s.zone()
                ))
                .toList();
        return new HoldCreateResponse(
                result.holdGroupId().toString(),
                result.scheduleId().toString(),
                seats,
                result.expiresAtMs(),
                result.holdTtlSec()
        );
    }
}
