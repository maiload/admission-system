package com.example.ticket.core.adapter.out.redis;

import com.example.ticket.common.key.RedisKeyBuilder;
import com.example.ticket.core.application.port.out.SoldOutPort;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.time.Duration;

@Component
@RequiredArgsConstructor
public class RedisSoldOutAdapter implements SoldOutPort {

    private final ReactiveStringRedisTemplate redis;

    @Override
    public Mono<Boolean> isSoldOut(String eventId, String scheduleId) {
        String key = RedisKeyBuilder.soldOut(eventId, scheduleId);
        return redis.hasKey(key);
    }

    @Override
    public Mono<Void> markSoldOut(String eventId, String scheduleId, long ttlSeconds) {
        String key = RedisKeyBuilder.soldOut(eventId, scheduleId);
        return redis.opsForValue()
                .set(key, "1", Duration.ofSeconds(ttlSeconds))
                .then();
    }
}
