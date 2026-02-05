package com.example.ticket.gate.application.port.in;

import reactor.core.publisher.Mono;

public interface SyncInPort {

    Mono<SyncResult> execute(Sync sync);

    record Sync(
            String eventId,
            String scheduleId
    ) {}

    record SyncResult(
            long serverTimeMs,
            long startAtMs,
            String syncToken
    ) {}
}
