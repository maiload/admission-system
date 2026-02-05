package com.example.ticket.core.adapter.in.web.request;

import com.example.ticket.core.application.dto.query.SeatQuery;
import java.util.UUID;

public record CoreSeatRequest(
        String eventId,
        String scheduleId
) {
    public SeatQuery toQuery(String sessionId) {
        return new SeatQuery(sessionId, eventId, UUID.fromString(scheduleId));
    }
}
