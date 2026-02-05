package com.example.ticket.core.adapter.out.redis;

import com.example.ticket.common.key.RedisKeyBuilder;
import com.example.ticket.core.application.port.out.SessionPort;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;

@Component
@RequiredArgsConstructor
public class RedisSessionAdapter implements SessionPort {

    private final ReactiveStringRedisTemplate redis;
    private final RedisScript<String> handshakeScript;
    private final int sessionTtlSec;

    @Override
    public Mono<String> handshake(String eventId, String scheduleId,
                                  String jti, String clientId, String sessionId) {
        List<String> keys = Arrays.asList(
                RedisKeyBuilder.enterToken(eventId, scheduleId, jti),
                RedisKeyBuilder.coreSession(eventId, scheduleId, sessionId),
                RedisKeyBuilder.activeSet(eventId, scheduleId)
        );

        return redis.execute(handshakeScript, keys,
                        Arrays.asList(clientId, sessionId, String.valueOf(sessionTtlSec),
                                RedisKeyBuilder.hashTag(eventId, scheduleId)))
                .next()
                .flatMap(result -> {
                    if ("INVALID".equals(result) || "MISMATCH".equals(result)) {
                        return Mono.empty();
                    }
                    // Result is clientId from enter token
                    return Mono.just(result);
                });
    }

    @Override
    public Mono<String> validateSession(String eventId, String scheduleId, String sessionId) {
        String key = RedisKeyBuilder.coreSession(eventId, scheduleId, sessionId);
        return redis.opsForValue().get(key);
    }

    @Override
    public Mono<Boolean> refreshSession(String eventId, String scheduleId, String sessionId) {
        String csKey = RedisKeyBuilder.coreSession(eventId, scheduleId, sessionId);
        Duration ttl = Duration.ofSeconds(sessionTtlSec);
        return redis.expire(csKey, ttl)
                .flatMap(ok -> {
                    if (Boolean.TRUE.equals(ok)) {
                        // Also refresh the index key
                        return redis.opsForValue().get(csKey)
                                .flatMap(clientId -> {
                                    String idxKey = RedisKeyBuilder.coreSessionIndex(eventId, scheduleId, clientId);
                                    return redis.expire(idxKey, ttl);
                                });
                    }
                    return Mono.just(false);
                });
    }

    @Override
    public Mono<Void> closeSession(String eventId, String scheduleId,
                                   String sessionId, String clientId) {
        String csKey = RedisKeyBuilder.coreSession(eventId, scheduleId, sessionId);
        String idxKey = RedisKeyBuilder.coreSessionIndex(eventId, scheduleId, clientId);
        String activeKey = RedisKeyBuilder.activeSet(eventId, scheduleId);

        return redis.delete(csKey, idxKey)
                .then(redis.opsForSet().remove(activeKey, clientId))
                .then();
    }

    @Override
    public Mono<Long> cleanupExpiredSessions(String eventId, String scheduleId) {
        String activeKey = RedisKeyBuilder.activeSet(eventId, scheduleId);

        return redis.opsForSet().members(activeKey)
                .flatMap(clientId -> {
                    String idxKey = RedisKeyBuilder.coreSessionIndex(eventId, scheduleId, clientId);
                    return redis.hasKey(idxKey)
                            .flatMap(exists -> {
                                if (Boolean.FALSE.equals(exists)) {
                                    return redis.opsForSet().remove(activeKey, clientId)
                                            .thenReturn(1L);
                                }
                                return Mono.just(0L);
                            });
                })
                .reduce(0L, Long::sum);
    }
}
