package com.example.ticket.gate.adapter.out.redis;

import com.example.ticket.common.key.RedisKeyBuilder;
import com.example.ticket.gate.application.port.out.SoldOutQueryPort;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import reactor.core.publisher.Mono;

@RequiredArgsConstructor
public class RedisSoldOutQuery implements SoldOutQueryPort {

    private final ReactiveRedisTemplate<String, String> redisTemplate;

    @Override
    public Mono<Boolean> isSoldOut(String eventId, String scheduleId) {
        String key = RedisKeyBuilder.soldOut(eventId, scheduleId);
        return redisTemplate.hasKey(key);
    }
}
