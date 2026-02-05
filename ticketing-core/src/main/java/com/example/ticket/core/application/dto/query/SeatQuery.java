package com.example.ticket.core.application.dto.query;

import java.util.UUID;

public record SeatQuery(
        String sessionId,
        String eventId,
        UUID scheduleId
) {
}
