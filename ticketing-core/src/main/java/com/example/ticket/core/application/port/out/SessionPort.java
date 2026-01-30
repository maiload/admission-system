package com.example.ticket.core.application.port.out;

import reactor.core.publisher.Mono;

public interface SessionPort {

    /**
     * Atomic handshake: validate + DEL enter_token → create core session → SADD active.
     * Returns clientId on success.
     */
    Mono<String> handshake(String eventId, String scheduleId, String jti, String clientId, String sessionId);

    /**
     * Validate coreSessionToken and return clientId.
     */
    Mono<String> validateSession(String eventId, String scheduleId, String sessionId);

    /**
     * Extend session TTL (sliding window).
     */
    Mono<Boolean> refreshSession(String eventId, String scheduleId, String sessionId);

    /**
     * Close session: DEL session keys + SREM active.
     */
    Mono<Void> closeSession(String eventId, String scheduleId, String sessionId, String clientId);

    /**
     * Scan active SET and remove members whose session expired.
     */
    Mono<Long> cleanupExpiredSessions(String eventId, String scheduleId);
}
