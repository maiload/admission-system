package com.example.ticket.core.application.port.out;

import reactor.core.publisher.Mono;

public interface SessionPort {

    Mono<String> handshake(HandshakeCommand command);

    Mono<String> validateSession(ValidateQuery query);

    Mono<Boolean> refreshSession(RefreshCommand command);

    Mono<Void> closeSession(CloseCommand command);

    Mono<Long> cleanupExpiredSessions(CleanupQuery query);

    record HandshakeCommand(String eventId, String scheduleId, String jti, String sessionId) {}

    record ValidateQuery(String eventId, String scheduleId, String sessionId) {}

    record RefreshCommand(String eventId, String scheduleId, String sessionId) {}

    record CloseCommand(String eventId, String scheduleId, String sessionId, String clientId) {}

    record CleanupQuery(String eventId, String scheduleId) {}
}
