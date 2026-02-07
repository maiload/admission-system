package com.example.ticket.admission.adapter.out.redis;

import com.example.ticket.common.key.RedisKeyBuilder;
import com.example.ticket.common.port.ClockPort;
import com.example.ticket.admission.application.dto.IssueResult;
import com.example.ticket.admission.application.port.out.IssuerPort;
import com.example.ticket.admission.domain.AdmissionConfig;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Range;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.List;

@RequiredArgsConstructor
public class RedisIssuer implements IssuerPort {

    private final ReactiveRedisTemplate<String, String> redisTemplate;
    private final RedisScript<List> issueScript;
    private final ClockPort clockPort;

    @Override
    public Mono<IssueResult> issue(String eventId, String scheduleId,
                                    AdmissionConfig config,
                                    List<String> tokenPairs) {

        List<String> keys = buildKeys(eventId, scheduleId);
        List<String> args = buildArgs(config, eventId, scheduleId, tokenPairs);

        return redisTemplate.execute(issueScript, keys, args)
                .next()
                .map(result -> {
                    @SuppressWarnings("unchecked")
                    List<Object> res = (List<Object>) result;
                    long issued = toLong(res.get(0));
                    long skipped = toLong(res.get(1));
                    long remaining = toLong(res.get(2));
                    return new IssueResult(issued, skipped, remaining);
                })
                .defaultIfEmpty(new IssueResult(0, 0, 0));
    }

    @Override
    public Mono<Boolean> isHeadReady(String eventId, String scheduleId, AdmissionConfig config) {
        if (!config.simulationEnabled() || config.exitRatePerSec() <= 0) {
            return Mono.just(true);
        }

        String queueKey = RedisKeyBuilder.queue(eventId, scheduleId);

        return redisTemplate.opsForZSet().range(queueKey, Range.closed(0L, 0L))
                .next()
                .flatMap(queueToken -> {
                    String stateKey = RedisKeyBuilder.queueState(eventId, scheduleId, queueToken);
                    return redisTemplate.opsForHash().multiGet(stateKey, List.of("joinedAtMs", "estimatedRank"))
                            .filter(values -> values.getFirst() != null && values.get(1) != null)
                            .map(values -> {
                                long joinedAtMs = Long.parseLong(values.get(0).toString());
                                long estimatedRank = Long.parseLong(values.get(1).toString());
                                long requiredMs = (estimatedRank * 1000L) / config.exitRatePerSec();
                                long readyAtMs = joinedAtMs + requiredMs;
                                return clockPort.nowMillis() >= readyAtMs;
                            })
                            .defaultIfEmpty(true);
                })
                .defaultIfEmpty(true);
    }

    private List<String> buildKeys(String eventId, String scheduleId) {
        long epochSecond = clockPort.nowEpochSecond();
        return List.of(
                RedisKeyBuilder.queue(eventId, scheduleId),
                RedisKeyBuilder.rateCounter(eventId, scheduleId, epochSecond),
                RedisKeyBuilder.activeSet(eventId, scheduleId)
        );
    }

    private List<String> buildArgs(AdmissionConfig config,
                                   String eventId,
                                   String scheduleId,
                                   List<String> tokenPairs) {
        String hashTag = RedisKeyBuilder.hashTag(eventId, scheduleId);
        int tokenCount = tokenPairs.size() / 2;

        List<String> args = new ArrayList<>();
        args.add(String.valueOf(config.maxBatch()));
        args.add(String.valueOf(config.rateCap()));
        args.add(String.valueOf(config.concurrencyCap()));
        args.add(String.valueOf(config.enterTtlSec()));
        args.add(String.valueOf(config.qstateTtlSec()));
        args.add(String.valueOf(config.rateTtlSec()));
        args.add(hashTag);
        args.add(String.valueOf(tokenCount));
        args.addAll(tokenPairs);
        return args;
    }

    private long toLong(Object obj) {
        if (obj instanceof Long l) return l;
        if (obj instanceof String s) return Long.parseLong(s);
        return Long.parseLong(obj.toString());
    }
}
