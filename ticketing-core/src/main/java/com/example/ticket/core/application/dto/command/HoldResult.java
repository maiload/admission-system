package com.example.ticket.core.application.dto.command;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.UUID;

@Getter
@RequiredArgsConstructor
public class HoldResult {

    private final UUID holdId;
    private final String scheduleId;
    private final UUID seatId;
    private final int seatNo;
    private final String zone;
    private final long expiresAtMs;
    private final int holdTtlSec;
}
