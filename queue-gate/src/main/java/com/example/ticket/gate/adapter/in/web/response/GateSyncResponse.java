package com.example.ticket.gate.adapter.in.web.response;

import com.example.ticket.gate.application.port.in.SyncInPort.SyncResult;

public record GateSyncResponse(
        long serverTimeMs,
        long startAtMs,
        String syncToken
) {
    public static GateSyncResponse fromResult(SyncResult result) {
        return new GateSyncResponse(
                result.serverTimeMs(),
                result.startAtMs(),
                result.syncToken()
        );
    }
}
