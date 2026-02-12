package com.example.ticket.core.application.port.in;

import reactor.core.publisher.Mono;

import java.util.UUID;

public interface EnterCoreInPort {

    Mono<EnterResult> execute(EnterCommand command);

    record EnterCommand(
            String enterToken,
            UUID eventId,
            UUID scheduleId
    ) {}

    record EnterResult(
            String sessionToken,
            int sessionTtlSec,
            UUID eventId,
            UUID scheduleId
    ) {}
}
