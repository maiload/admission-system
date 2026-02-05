package com.example.ticket.gate.adapter.out.redis;

import com.example.ticket.common.key.RedisKeyBuilder;
import com.example.ticket.gate.application.port.in.JoinQueueInPort.JoinResult;
import com.example.ticket.gate.application.port.in.StreamQueueInPort.ProgressResult;
import com.example.ticket.gate.application.port.out.QueueRepositoryPort;
import com.example.ticket.gate.application.port.out.QueueRepositoryPort.JoinCommand;
import com.example.ticket.gate.application.port.out.QueueRepositoryPort.SizeQuery;
import com.example.ticket.gate.application.port.out.QueueRepositoryPort.StateQuery;
import com.example.ticket.gate.config.GateProperties;
import com.example.ticket.gate.domain.QueueStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import reactor.core.publisher.Mono;

import java.util.List;

@RequiredArgsConstructor
public class RedisQueueRepository implements QueueRepositoryPort {

    private final ReactiveRedisTemplate<String, String> redisTemplate;
    private final RedisScript<List> joinScript;
    private final GateProperties gateProperties;

    @Override
    public Mono<JoinResult> join(JoinCommand command) {
        List<String> keys = List.of(
                RedisKeyBuilder.queueJoin(command.eventId(), command.scheduleId(), command.clientId()),
                RedisKeyBuilder.queue(command.eventId(), command.scheduleId()),
                RedisKeyBuilder.queueState(command.eventId(), command.scheduleId(), command.queueToken()),
                RedisKeyBuilder.queueSeq(command.eventId(), command.scheduleId())
        );

        List<String> args = List.of(
                command.clientId(),
                command.queueToken(),
                String.valueOf(command.estimatedRank()),
                String.valueOf(command.ttlSec())
        );

        return redisTemplate.execute(joinScript, keys, args)
                .next()
                .map(result -> {
                    @SuppressWarnings("unchecked")
                    List<String> res = (List<String>) result;
                    boolean alreadyJoined = "EXISTING".equals(res.get(0));
                    String returnedToken = res.get(1);
                    long returnedRank = Long.parseLong(res.get(2));
                    String sseUrl = "/gate/stream?queueToken=" + returnedToken;
                    return new JoinResult(returnedToken, returnedRank, sseUrl, alreadyJoined);
                });
    }

    @Override
    public Mono<ProgressResult> getState(StateQuery query) {
        String stateKey = RedisKeyBuilder.queueState(query.eventId(), query.scheduleId(), query.queueToken());

        return redisTemplate.opsForHash().entries(stateKey)
                .collectMap(e -> e.getKey().toString(), e -> e.getValue().toString())
                .filter(map -> !map.isEmpty())
                .map(map -> {
                    QueueStatus status = QueueStatus.valueOf(
                            map.getOrDefault("status", "EXPIRED"));
                    long rank = Long.parseLong(map.getOrDefault("estimatedRank", "0"));
                    rank = simulateRankIfEnabled(rank, map.get("joinedAtMs"));
                    String enterToken = map.get("enterToken");

                    return new ProgressResult(
                            status, rank, 0, enterToken,
                            query.eventId(), query.scheduleId()
                    );
                });
    }

    @Override
    public Mono<Long> getQueueSize(SizeQuery query) {
        String queueKey = RedisKeyBuilder.queue(query.eventId(), query.scheduleId());
        return redisTemplate.opsForZSet().size(queueKey)
                .defaultIfEmpty(0L);
    }

    // 시뮬레이션에서 경과된 시간과 이탈률에 비례해서 대기열을 감소시키는 함수
    private long simulateRankIfEnabled(long estimatedRank, String joinedAtMsRaw) {
        GateProperties.Sync.Simulation simulation = gateProperties.sync().simulation();
        if (!simulation.enabled() || simulation.exitRatePerSec() <= 0 || joinedAtMsRaw == null) {
            return estimatedRank;
        }
        long joinedAtMs;
        try {
            joinedAtMs = Long.parseLong(joinedAtMsRaw);
        } catch (NumberFormatException e) {
            return estimatedRank;
        }
        long elapsedSec = Math.max(0L, (System.currentTimeMillis() - joinedAtMs) / 1000L);
        long decreased = simulation.exitRatePerSec() * elapsedSec;
        return Math.max(1L, estimatedRank - decreased);
    }
}
