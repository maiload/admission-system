package com.example.ticket.gate.adapter.out.redis;

import com.example.ticket.common.key.RedisKeyBuilder;
import com.example.ticket.gate.application.port.out.ScheduleQueryPort;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import reactor.core.publisher.Mono;

@RequiredArgsConstructor
public class RedisScheduleQuery implements ScheduleQueryPort {

    private final ReactiveRedisTemplate<String, String> redisTemplate;

    @Override
    public Mono<Long> getStartAtMs(Query query) {
        String key = RedisKeyBuilder.activeSchedules();
        String member = query.eventId() + ":" + query.scheduleId();

        return redisTemplate.opsForZSet().score(key, member)
                .map(Double::longValue);
    }
}
