package com.example.ticket.core.adapter.in.web.response;

import com.example.ticket.core.application.port.in.EnterCoreInPort.EnterResult;

public record CoreEnterResponse(
        int sessionTtlSec,
        String eventId,
        String scheduleId
) {
    public static CoreEnterResponse fromResult(EnterResult result) {
        return new CoreEnterResponse(
                result.sessionTtlSec(),
                result.eventId().toString(),
                result.scheduleId().toString()
        );
    }
}
