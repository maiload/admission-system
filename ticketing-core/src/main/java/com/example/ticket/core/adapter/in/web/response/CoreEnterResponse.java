package com.example.ticket.core.adapter.in.web.response;

import com.example.ticket.core.application.dto.command.EnterResult;

public record CoreEnterResponse(
        int sessionTtlSec,
        String eventId,
        String scheduleId
) {
    public static CoreEnterResponse fromResult(EnterResult result) {
        return new CoreEnterResponse(
                result.expiresInSec(),
                result.eventId(),
                result.scheduleId()
        );
    }
}
