package com.example.ticket.core.adapter.out.redis;

import com.example.ticket.common.key.RedisKeyBuilder;
import com.example.ticket.core.application.port.out.ActiveScheduleWritePort;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Component
@RequiredArgsConstructor
public class RedisActiveScheduleWriter implements ActiveScheduleWritePort {

    private final ReactiveStringRedisTemplate redisTemplate;

    @Override
    public Mono<Boolean> upsert(String eventId, String scheduleId, long startAtMs) {
        String key = RedisKeyBuilder.activeSchedules();
        String member = eventId + ":" + scheduleId;
        return redisTemplate.opsForZSet().add(key, member, startAtMs);
    }

    @Override
    public Mono<Long> clearAll() {
        return redisTemplate.delete(RedisKeyBuilder.activeSchedules());
    }
}
