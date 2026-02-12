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
    public Mono<Boolean> isSoldOut(Query query) {
        String key = RedisKeyBuilder.soldOut(query.eventId(), query.scheduleId());
        return redisTemplate.hasKey(key);
    }
}
