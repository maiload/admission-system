package com.example.ticket.core.adapter.in.web.response;

import com.example.ticket.core.application.dto.query.ZoneSeatsView;
import java.util.List;

public record CoreSeatResponse(
        String eventId,
        String scheduleId,
        List<ZoneSeatsView> zones,
        long serverTimeMs
) {
    public static CoreSeatResponse fromResult(
            String eventId,
            String scheduleId,
            List<ZoneSeatsView> zones
    ) {
        return new CoreSeatResponse(eventId, scheduleId, zones, System.currentTimeMillis());
    }
}
