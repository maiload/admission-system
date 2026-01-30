package com.example.ticket.admission.application.port.out;

import java.util.List;

/**
 * Generates enter token (jti + HMAC-signed token) pairs.
 */
public interface TokenGeneratorPort {

    /**
     * Generate a batch of token pairs.
     *
     * @param eventId    event ID
     * @param scheduleId schedule ID
     * @param count      number of pairs to generate
     * @return flat list: [jti1, token1, jti2, token2, ...]
     */
    List<String> generateBatch(String eventId, String scheduleId, int count);
}
