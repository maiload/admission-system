package com.example.ticket.common.util;

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


    public static String joinClaims(String... parts) {
        return String.join(CLAIM_SEPARATOR, parts);
    }


    public static String[] splitClaims(String payload) {
        return payload.split("\\" + CLAIM_SEPARATOR);
    }
}
