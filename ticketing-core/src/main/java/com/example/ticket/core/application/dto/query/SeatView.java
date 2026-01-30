package com.example.ticket.core.application.dto.query;

import com.example.ticket.core.domain.seat.SeatStatus;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.UUID;

@Getter
@RequiredArgsConstructor
public class SeatView {

    private final UUID seatId;
    private final String zone;
    private final int seatNo;
    private final SeatStatus status;
}
