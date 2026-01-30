package com.example.ticket.common.util;

/**
 * Token prefix constants and formatting utilities.
 */
public final class TokenFormat {

    public static final String QUEUE_TOKEN_PREFIX = "qt_";
    public static final String CORE_SESSION_PREFIX = "cs_";
    public static final String CLAIM_SEPARATOR = "|";

    private TokenFormat() {
    }

    public static String queueToken(String uuid) {
        return QUEUE_TOKEN_PREFIX + uuid;
    }

    public static String coreSessionId(String uuid) {
        return CORE_SESSION_PREFIX + uuid;
    }

    /**
     * Join multiple claim values into a single payload string.
     * e.g. joinClaims("eventId", "scheduleId", "12345") → "eventId|scheduleId|12345"
     */
    public static String joinClaims(String... parts) {
        return String.join(CLAIM_SEPARATOR, parts);
    }

    /**
     * Split a payload string back into claim values.
     */
    public static String[] splitClaims(String payload) {
        return payload.split("\\" + CLAIM_SEPARATOR);
    }
}
