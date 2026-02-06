package com.example.ticket.core.adapter.in.web.response;

import com.example.ticket.core.application.port.in.HoldInPort.ConfirmHoldResult;

public record HoldConfirmResponse(
        String reservationId,
        String scheduleId,
        String seatId,
        String zone,
        int seatNo,
        long confirmedAtMs
) {
    public static HoldConfirmResponse fromResult(ConfirmHoldResult result) {
        return new HoldConfirmResponse(
                result.reservationId().toString(),
                result.scheduleId(),
                result.seatId(),
                result.zone(),
                result.seatNo(),
                result.confirmedAtMs()
        );
    }
}
