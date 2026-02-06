package com.example.ticket.core.domain;

import java.time.Instant;
import java.util.UUID;

public record Reservation(
        UUID id,
        UUID scheduleId,
        UUID seatId,
        String clientId,
        Instant confirmedAt
) {}
