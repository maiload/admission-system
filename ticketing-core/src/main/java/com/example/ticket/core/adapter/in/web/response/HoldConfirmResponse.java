package com.example.ticket.core.adapter.in.web.response;

import com.example.ticket.core.application.port.in.HoldInPort.ConfirmHoldResult;

import java.util.List;

public record HoldConfirmResponse(
        String scheduleId,
        List<ConfirmedSeatResponse> seats,
        long confirmedAtMs
) {
    public record ConfirmedSeatResponse(
            String reservationId,
            String seatId,
            String zone,
            int seatNo
    ) {}

    public static HoldConfirmResponse fromResult(ConfirmHoldResult result) {
        List<ConfirmedSeatResponse> seats = result.seats().stream()
                .map(s -> new ConfirmedSeatResponse(
                        s.reservationId().toString(),
                        s.seatId().toString(),
                        s.zone(),
                        s.seatNo()
                ))
                .toList();
        return new HoldConfirmResponse(
                result.scheduleId().toString(),
                seats,
                result.confirmedAtMs()
        );
    }
}
