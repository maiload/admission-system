package com.example.ticket.gate.adapter.in.web.request;

import com.example.ticket.gate.application.port.in.SyncInPort.Sync;

public record GateSyncRequest(
        String eventId,
        String scheduleId
) {
    public Sync toQuery() {
        return new Sync(eventId, scheduleId);
    }
}
