package com.example.ticket.core.adapter.in.web.request;

import com.example.ticket.core.application.port.in.SeatQueryInPort.SeatQuery;
import java.util.UUID;

public record CoreSeatRequest(
        String eventId,
        String scheduleId
) {
    public SeatQuery toQuery(String sessionId) {
        return new SeatQuery(UUID.fromString(eventId), UUID.fromString(scheduleId), sessionId);
    }
}
