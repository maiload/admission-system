package com.example.ticket.gate.adapter.out.redis;

import com.example.ticket.common.key.RedisKeyBuilder;
import com.example.ticket.gate.application.dto.JoinResult;
import com.example.ticket.gate.application.dto.QueueProgressDto;
import com.example.ticket.gate.application.port.out.QueueRepositoryPort;
import com.example.ticket.gate.domain.QueueStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import reactor.core.publisher.Mono;

import java.util.List;

@RequiredArgsConstructor
public class RedisQueueRepository implements QueueRepositoryPort {

    private final ReactiveRedisTemplate<String, String> redisTemplate;
    private final RedisScript<List> joinScript;

    @Override
    public Mono<JoinResult> join(String eventId, String scheduleId, String clientId,
                                  String queueToken, double score, long estimatedRank, int ttlSec) {
        List<String> keys = List.of(
                RedisKeyBuilder.queueJoin(eventId, scheduleId, clientId),
                RedisKeyBuilder.queue(eventId, scheduleId),
                RedisKeyBuilder.queueState(eventId, scheduleId, queueToken)
        );

        List<String> args = List.of(
                clientId,
                queueToken,
                String.valueOf(score),
                String.valueOf(estimatedRank),
                String.valueOf(ttlSec)
        );

        return redisTemplate.execute(joinScript, keys, args)
                .next()
                .map(result -> {
                    @SuppressWarnings("unchecked")
                    List<String> res = (List<String>) result;
                    // Lua returns: { "EXISTING"|"CREATED", queueToken, estimatedRank }
                    boolean alreadyJoined = "EXISTING".equals(res.get(0));
                    String returnedToken = res.get(1);
                    long returnedRank = Long.parseLong(res.get(2));
                    String sseUrl = "/gate/stream?queueToken=" + returnedToken;
                    return new JoinResult(returnedToken, returnedRank, sseUrl, alreadyJoined);
                });
    }

    @Override
    public Mono<QueueProgressDto> getState(String eventId, String scheduleId, String queueToken) {
        String stateKey = RedisKeyBuilder.queueState(eventId, scheduleId, queueToken);

        return redisTemplate.opsForHash().entries(stateKey)
                .collectMap(e -> e.getKey().toString(), e -> e.getValue().toString())
                .filter(map -> !map.isEmpty())
                .map(map -> {
                    QueueStatus status = QueueStatus.valueOf(
                            map.getOrDefault("status", "EXPIRED"));
                    long rank = Long.parseLong(map.getOrDefault("estimatedRank", "0"));
                    String enterToken = map.get("enterToken");

                    return new QueueProgressDto(
                            status, rank, 0, enterToken,
                            eventId, scheduleId, System.currentTimeMillis()
                    );
                });
    }

    @Override
    public Mono<Long> getQueueSize(String eventId, String scheduleId) {
        String queueKey = RedisKeyBuilder.queue(eventId, scheduleId);
        return redisTemplate.opsForZSet().size(queueKey)
                .defaultIfEmpty(0L);
    }
}
