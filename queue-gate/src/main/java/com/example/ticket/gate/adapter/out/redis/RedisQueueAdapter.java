package com.example.ticket.gate.adapter.out.redis;

import com.example.ticket.common.key.RedisKeyBuilder;
import com.example.ticket.gate.application.port.in.JoinQueueInPort.JoinResult;
import com.example.ticket.gate.application.port.in.StreamQueueInPort.ProgressResult;
import com.example.ticket.gate.application.port.out.QueueRepositoryPort;
import com.example.ticket.gate.domain.QueueStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.Objects;
import java.util.List;

@RequiredArgsConstructor
public class RedisQueueAdapter implements QueueRepositoryPort {

    private final ReactiveRedisTemplate<String, String> redisTemplate;
    private final RedisScript<List> joinScript;

    @Override
    public Mono<JoinResult> join(JoinCommand command) {
        List<String> keys = buildKeys(command);
        List<String> args = buildArgs(command);

        return redisTemplate.execute(joinScript, keys, args)
                .next()
                .map(result -> {
                    @SuppressWarnings("unchecked")
                    List<Object> res = (List<Object>) result;
                    boolean alreadyJoined = "EXISTING".equals(String.valueOf(res.get(0)));
                    String returnedToken = String.valueOf(res.get(1));
                    String sseUrl = "/gate/stream?queueToken=" + returnedToken;
                    return new JoinResult(returnedToken, sseUrl, alreadyJoined);
                });
    }

    @Override
    public Mono<ProgressResult> getState(StateQuery query) {
        String stateKey = RedisKeyBuilder.queueState(query.eventId(), query.scheduleId(), query.queueToken());

        return redisTemplate.opsForHash().entries(stateKey)
                .collectMap(e -> e.getKey().toString(), e -> e.getValue().toString())
                .filter(map -> !map.isEmpty())
                .flatMap(map -> {
                    QueueStatus status = QueueStatus.valueOf(
                            map.getOrDefault("status", "EXPIRED"));
                    String enterToken = map.get("enterToken");
                    if (status != QueueStatus.WAITING) {
                        return Mono.just(new ProgressResult(
                                status, 0, 0, enterToken,
                                query.eventId(), query.scheduleId()
                        ));
                    }
                    String queueKey = RedisKeyBuilder.queue(query.eventId(), query.scheduleId());
                    return redisTemplate.opsForZSet()
                            .rank(queueKey, query.queueToken())
                            .map(r -> r + 1)
                            .defaultIfEmpty(0L)
                            .map(rank -> new ProgressResult(
                                    status, rank, 0, enterToken,
                                    query.eventId(), query.scheduleId()
                            ));
                });
    }

    @Override
    public Mono<Long> getQueueSize(SizeQuery query) {
        String queueKey = RedisKeyBuilder.queue(query.eventId(), query.scheduleId());
        return redisTemplate.opsForZSet().size(queueKey)
                .defaultIfEmpty(0L);
    }

    @Override
    public Mono<Void> refreshStateTtl(StateTtlCommand command) {
        String stateKey = RedisKeyBuilder.queueState(
                command.eventId(), command.scheduleId(), command.queueToken());
        return redisTemplate.getExpire(stateKey)
                .filter(this::isValidTtl)
                .map(Duration::getSeconds)
                .filter(ttlSec -> ttlSec <= command.refreshThresholdSec())
                .flatMap(sec -> redisTemplate.expire(
                        stateKey,
                        Duration.ofSeconds(command.ttlSec())
                ))
                .then();
    }

    private boolean isValidTtl(Duration ttl) {
        return ttl != null && !ttl.isNegative() && !ttl.isZero();
    }

    private List<String> buildKeys(JoinCommand command) {
        return List.of(
                RedisKeyBuilder.queueJoin(command.eventId(), command.scheduleId(), command.clientId()),
                RedisKeyBuilder.queue(command.eventId(), command.scheduleId()),
                RedisKeyBuilder.queueState(command.eventId(), command.scheduleId(), command.queueToken()),
                RedisKeyBuilder.queueSeq(command.eventId(), command.scheduleId())
        );
    }

    private List<String> buildArgs(JoinCommand command) {
        return List.of(
                command.clientId(),
                command.queueToken(),
                String.valueOf(command.stateTtlSec()),
                command.loadTest() ? "1" : "0"
        );
    }
}
