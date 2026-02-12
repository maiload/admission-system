package com.example.ticket.common.key;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class RedisKeyBuilderTest {

    private static final String EVENT = "ev1";
    private static final String SCHEDULE = "sc1";

    @Test
    void queue_containsHashTag() {
        String key = RedisKeyBuilder.queue(EVENT, SCHEDULE);
        assertEquals("q:{ev1}:{sc1}:z", key);
        assertTrue(key.contains("{" + EVENT + "}"));
    }

    @Test
    void queueState_containsTokenAndHashTag() {
        String key = RedisKeyBuilder.queueState(EVENT, SCHEDULE, "qt_abc");
        assertEquals("qstate:{ev1}:{sc1}:qt_abc", key);
    }

    @Test
    void queueJoin_containsClientId() {
        String key = RedisKeyBuilder.queueJoin(EVENT, SCHEDULE, "cid_123");
        assertEquals("qjoin:{ev1}:{sc1}:cid_123", key);
    }

    @Test
    void enterToken_containsJti() {
        String key = RedisKeyBuilder.enterToken(EVENT, SCHEDULE, "jti_xyz");
        assertEquals("enter:{ev1}:{sc1}:jti_xyz", key);
    }

    @Test
    void rateCounter_containsEpochSecond() {
        String key = RedisKeyBuilder.rateCounter(EVENT, SCHEDULE, 1700000000L);
        assertEquals("rate:{ev1}:{sc1}:1700000000", key);
    }

    @Test
    void activeSet() {
        assertEquals("active:{ev1}:{sc1}", RedisKeyBuilder.activeSet(EVENT, SCHEDULE));
    }

    @Test
    void coreSession_containsSessionId() {
        String key = RedisKeyBuilder.coreSession(EVENT, SCHEDULE, "sid_001");
        assertEquals("cs:{ev1}:{sc1}:sid_001", key);
    }

    @Test
    void coreSessionIndex_containsClientId() {
        String key = RedisKeyBuilder.coreSessionIndex(EVENT, SCHEDULE, "cid_123");
        assertEquals("csidx:{ev1}:{sc1}:cid_123", key);
    }

    @Test
    void soldOut() {
        assertEquals("soldout:{ev1}:{sc1}", RedisKeyBuilder.soldOut(EVENT, SCHEDULE));
    }

    @Test
    void activeSchedules_noHashTag() {
        assertEquals("active_schedules", RedisKeyBuilder.activeSchedules());
    }

    @Test
    void allKeysForSameEventShareHashTag() {
        String tag = "{ev1}:{sc1}";
        assertTrue(RedisKeyBuilder.queue(EVENT, SCHEDULE).contains(tag));
        assertTrue(RedisKeyBuilder.queueState(EVENT, SCHEDULE, "qt").contains(tag));
        assertTrue(RedisKeyBuilder.queueJoin(EVENT, SCHEDULE, "cid").contains(tag));
        assertTrue(RedisKeyBuilder.enterToken(EVENT, SCHEDULE, "jti").contains(tag));
        assertTrue(RedisKeyBuilder.rateCounter(EVENT, SCHEDULE, 0).contains(tag));
        assertTrue(RedisKeyBuilder.activeSet(EVENT, SCHEDULE).contains(tag));
        assertTrue(RedisKeyBuilder.coreSession(EVENT, SCHEDULE, "sid").contains(tag));
        assertTrue(RedisKeyBuilder.coreSessionIndex(EVENT, SCHEDULE, "cid").contains(tag));
        assertTrue(RedisKeyBuilder.soldOut(EVENT, SCHEDULE).contains(tag));
    }
}
