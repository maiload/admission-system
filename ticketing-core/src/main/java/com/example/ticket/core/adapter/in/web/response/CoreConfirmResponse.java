package com.example.ticket.core.adapter.in.web.response;

import com.example.ticket.core.application.dto.command.ConfirmResult;

public record CoreConfirmResponse(
        String reservationId,
        String scheduleId,
        String seatId,
        String zone,
        int seatNo,
        long confirmedAtMs
) {
    public static CoreConfirmResponse fromResult(ConfirmResult result) {
        return new CoreConfirmResponse(
                result.reservationId().toString(),
                result.scheduleId(),
                result.seatId().toString(),
                result.zone(),
                result.seatNo(),
                result.confirmedAtMs()
        );
    }
}
