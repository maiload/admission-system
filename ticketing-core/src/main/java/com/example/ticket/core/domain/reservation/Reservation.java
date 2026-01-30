package com.example.ticket.core.domain.reservation;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Getter
@RequiredArgsConstructor
public class Reservation {

    private final UUID id;
    private final UUID scheduleId;
    private final UUID seatId;
    private final String clientId;
    private final Instant confirmedAt;
}
