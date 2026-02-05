package com.example.ticket.common.key;

public final class RedisKeyBuilder {

    private RedisKeyBuilder() {
    }

    private static String tag(String eventId, String scheduleId) {
        return "{" + eventId + "}:{" + scheduleId + "}";
    }

    // Queue ZSET: member=queueToken, score=rank+seqFraction
    public static String queue(String eventId, String scheduleId) {
        return "q:" + tag(eventId, scheduleId) + ":z";
    }

    // Queue token state HASH
    public static String queueState(String eventId, String scheduleId, String queueToken) {
        return "qstate:" + tag(eventId, scheduleId) + ":" + queueToken;
    }

    // Duplicate join prevention: clientId → queueToken
    public static String queueJoin(String eventId, String scheduleId, String clientId) {
        return "qjoin:" + tag(eventId, scheduleId) + ":" + clientId;
    }

    // Queue score sequence counter
    public static String queueSeq(String eventId, String scheduleId) {
        return "qseq:" + tag(eventId, scheduleId);
    }

    // Enter token: jti → clientId
    public static String enterToken(String eventId, String scheduleId, String jti) {
        return "enter:" + tag(eventId, scheduleId) + ":" + jti;
    }

    // Rate counter per second
    public static String rateCounter(String eventId, String scheduleId, long epochSecond) {
        return "rate:" + tag(eventId, scheduleId) + ":" + epochSecond;
    }

    // Active sessions SET: member=clientId
    public static String activeSet(String eventId, String scheduleId) {
        return "active:" + tag(eventId, scheduleId);
    }

    // Core session: sessionId → clientId
    public static String coreSession(String eventId, String scheduleId, String sessionId) {
        return "cs:" + tag(eventId, scheduleId) + ":" + sessionId;
    }

    // Core session index: clientId → sessionId
    public static String coreSessionIndex(String eventId, String scheduleId, String clientId) {
        return "csidx:" + tag(eventId, scheduleId) + ":" + clientId;
    }

    // Sold out flag
    public static String soldOut(String eventId, String scheduleId) {
        return "soldout:" + tag(eventId, scheduleId);
    }

    // Active schedules ZSET (global, no hash tag)
    public static String activeSchedules() {
        return "active_schedules";
    }

    // Returns the hash tag portion for Lua key construction
    public static String hashTag(String eventId, String scheduleId) {
        return tag(eventId, scheduleId);
    }
}
