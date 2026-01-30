package com.example.ticket.gate.application.port.out;

public interface TokenSignerPort {

    /**
     * Create a signed sync token.
     */
    String signSyncToken(String payload);

    /**
     * Verify and extract sync token payload.
     * @return payload or null if invalid
     */
    String verifySyncToken(String token);
}
