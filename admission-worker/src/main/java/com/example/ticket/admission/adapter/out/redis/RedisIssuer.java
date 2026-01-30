package com.example.ticket.admission.adapter.out.redis;

import com.example.ticket.common.key.RedisKeyBuilder;
import com.example.ticket.common.port.ClockPort;
import com.example.ticket.admission.application.dto.IssueResult;
import com.example.ticket.admission.application.port.out.IssuerPort;
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
    private final ClockPort clock;

    @Override
    public Mono<IssueResult> issue(String eventId, String scheduleId,
                                    int maxIssue, int rateCap, int concurrencyCap,
                                    int enterTtlSec, int qstateTtlSec, int rateTtlSec,
                                    List<String> tokenPairs) {
        long epochSecond = clock.nowEpochSecond();

        List<String> keys = List.of(
                RedisKeyBuilder.queue(eventId, scheduleId),
                RedisKeyBuilder.rateCounter(eventId, scheduleId, epochSecond),
                RedisKeyBuilder.activeSet(eventId, scheduleId)
        );

        String hashTag = RedisKeyBuilder.hashTag(eventId, scheduleId);
        int tokenCount = tokenPairs.size() / 2;

        List<String> args = new ArrayList<>();
        args.add(String.valueOf(maxIssue));
        args.add(String.valueOf(rateCap));
        args.add(String.valueOf(concurrencyCap));
        args.add(String.valueOf(enterTtlSec));
        args.add(String.valueOf(qstateTtlSec));
        args.add(String.valueOf(rateTtlSec));
        args.add(hashTag);
        args.add(String.valueOf(tokenCount));
        args.addAll(tokenPairs);

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

    private long toLong(Object obj) {
        if (obj instanceof Long l) return l;
        if (obj instanceof String s) return Long.parseLong(s);
        return Long.parseLong(obj.toString());
    }
}
