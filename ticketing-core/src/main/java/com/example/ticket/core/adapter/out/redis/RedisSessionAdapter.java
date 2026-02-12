package com.example.ticket.core.adapter.out.redis;

import com.example.ticket.common.key.RedisKeyBuilder;
import com.example.ticket.core.application.port.out.SessionPort;
import com.example.ticket.core.config.CoreProperties;
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
    private final CoreProperties coreProperties;

    @Override
    public Mono<String> handshake(HandshakeCommand command) {
        List<String> keys = buildHandshakeKeys(command);
        List<String> args = buildHandshakeArgs(command);

        return redisTemplate.execute(handshakeScript, keys, args)
                .next()
                .filter(result -> !"INVALID".equals(result));
    }

    @Override
    public Mono<String> validateSession(ValidateQuery query) {
        String csKey = RedisKeyBuilder.coreSession(query.eventId(), query.scheduleId(), query.sessionId());
        return redisTemplate.opsForValue().get(csKey);
    }

    @Override
    public Mono<Boolean> refreshSession(RefreshCommand command) {
        String csKey = RedisKeyBuilder.coreSession(command.eventId(), command.scheduleId(), command.sessionId());
        int sessionTtlSec = coreProperties.session().ttlSec();
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

    private List<String> buildHandshakeKeys(HandshakeCommand command) {
        return Arrays.asList(
                RedisKeyBuilder.enterToken(command.eventId(), command.scheduleId(), command.jti()),
                RedisKeyBuilder.coreSession(command.eventId(), command.scheduleId(), command.sessionId()),
                RedisKeyBuilder.activeSet(command.eventId(), command.scheduleId())
        );
    }

    private List<String> buildHandshakeArgs(HandshakeCommand command) {
        int sessionTtlSec = coreProperties.session().ttlSec();
        int loadTestTtlSec = coreProperties.test().loadTtlSec();
        return Arrays.asList(
                command.sessionId(),
                String.valueOf(sessionTtlSec),
                RedisKeyBuilder.hashTag(command.eventId(), command.scheduleId()),
                String.valueOf(loadTestTtlSec)
        );
    }
}
