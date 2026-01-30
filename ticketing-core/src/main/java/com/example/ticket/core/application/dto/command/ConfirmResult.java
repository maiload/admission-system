package com.example.ticket.core.application.dto.command;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.UUID;

@Getter
@RequiredArgsConstructor
public class ConfirmResult {

    private final UUID reservationId;
    private final String scheduleId;
    private final UUID seatId;
    private final String zone;
    private final int seatNo;
    private final long confirmedAtMs;
}
