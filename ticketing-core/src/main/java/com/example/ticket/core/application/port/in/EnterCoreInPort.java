package com.example.ticket.core.application.port.in;

import reactor.core.publisher.Mono;

public interface EnterCoreInPort {

    Mono<EnterResult> execute(EnterCommand command);

    record EnterCommand(
            String enterToken,
            String eventId,
            String scheduleId
    ) {}

    record EnterResult(
            String sessionToken,
            int sessionTtlSec,
            String eventId,
            String scheduleId
    ) {}
}
