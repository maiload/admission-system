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

    private final ReactiveStringRedisTemplate redisTemplate;
    private final RedisScript<String> handshakeScript;
    private final int sessionTtlSec;

    @Override
    public Mono<String> handshake(HandshakeCommand command) {
        List<String> keys = buildHandshakeKeys(command);
        List<String> args = buildHandshakeArgs(command);

        return redisTemplate.execute(handshakeScript, keys, args)
                .next()
                .filter(this::isValidHandshakeResult);
    }

    @Override
    public Mono<String> validateSession(ValidateQuery query) {
        String key = RedisKeyBuilder.coreSession(query.eventId(), query.scheduleId(), query.sessionId());
        return redisTemplate.opsForValue().get(key);
    }

    @Override
    public Mono<Boolean> refreshSession(RefreshCommand command) {
        String csKey = RedisKeyBuilder.coreSession(command.eventId(), command.scheduleId(), command.sessionId());
        Duration ttl = Duration.ofSeconds(sessionTtlSec);
        return redisTemplate.expire(csKey, ttl)
                .filter(Boolean.TRUE::equals)
                .flatMap(ignored ->
                        redisTemplate.opsForValue().get(csKey)
                                .flatMap(clientId -> {
                                    String idxKey = RedisKeyBuilder.coreSessionIndex(
                                            command.eventId(), command.scheduleId(), clientId);
                                    return redisTemplate.expire(idxKey, ttl);
                                })
                )
                .defaultIfEmpty(false);
    }

    @Override
    public Mono<Void> closeSession(CloseCommand command) {
        String csKey = RedisKeyBuilder.coreSession(command.eventId(), command.scheduleId(), command.sessionId());
        String idxKey = RedisKeyBuilder.coreSessionIndex(
                command.eventId(), command.scheduleId(), command.clientId());
        String activeKey = RedisKeyBuilder.activeSet(command.eventId(), command.scheduleId());

        return redisTemplate.delete(csKey, idxKey)
                .then(redisTemplate.opsForSet().remove(activeKey, command.clientId()))
                .then();
    }

    @Override
    public Mono<Long> cleanupExpiredSessions(CleanupQuery query) {
        String activeKey = RedisKeyBuilder.activeSet(query.eventId(), query.scheduleId());

        return redisTemplate.opsForSet().members(activeKey)
                .flatMap(clientId -> {
                    String idxKey = RedisKeyBuilder.coreSessionIndex(
                            query.eventId(), query.scheduleId(), clientId);
                    return redisTemplate.hasKey(idxKey)
                            .filter(Boolean.FALSE::equals)
                            .flatMap(ignored ->
                                    redisTemplate.opsForSet().remove(activeKey, clientId)
                                            .thenReturn(1L)
                            )
                            .defaultIfEmpty(0L);
                })
                .reduce(0L, Long::sum);
    }

    private boolean isValidHandshakeResult(String result) {
        return !("INVALID".equals(result) || "MISMATCH".equals(result));
    }

    private List<String> buildHandshakeKeys(HandshakeCommand command) {
        return Arrays.asList(
                RedisKeyBuilder.enterToken(command.eventId(), command.scheduleId(), command.jti()),
                RedisKeyBuilder.coreSession(command.eventId(), command.scheduleId(), command.sessionId()),
                RedisKeyBuilder.activeSet(command.eventId(), command.scheduleId())
        );
    }

    private List<String> buildHandshakeArgs(HandshakeCommand command) {
        return Arrays.asList(
                command.sessionId(),
                String.valueOf(sessionTtlSec),
                RedisKeyBuilder.hashTag(command.eventId(), command.scheduleId())
        );
    }
}
