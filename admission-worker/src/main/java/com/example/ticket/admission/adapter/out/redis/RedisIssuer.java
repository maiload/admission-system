package com.example.ticket.admission.adapter.out.redis;

import com.example.ticket.common.key.RedisKeyBuilder;
import com.example.ticket.common.port.ClockPort;
import com.example.ticket.admission.application.port.out.IssuerPort;
import com.example.ticket.admission.domain.AdmissionConfig;
import lombok.RequiredArgsConstructor;
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
    public Mono<IssuerPort.IssueResult> issue(String eventId, String scheduleId,
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
                    return new IssuerPort.IssueResult(issued, skipped, remaining);
                })
                .defaultIfEmpty(new IssuerPort.IssueResult(0, 0, 0));
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
