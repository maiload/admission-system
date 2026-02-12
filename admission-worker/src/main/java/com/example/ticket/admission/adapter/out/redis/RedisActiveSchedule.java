package com.example.ticket.admission.adapter.out.redis;

import com.example.ticket.common.key.RedisKeyBuilder;
import com.example.ticket.admission.application.port.out.ActiveSchedulePort;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Range;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import reactor.core.publisher.Flux;

@RequiredArgsConstructor
public class RedisActiveSchedule implements ActiveSchedulePort {

    private final ReactiveRedisTemplate<String, String> redisTemplate;

    @Override
    public Flux<String> getActiveSchedules() {
        String key = RedisKeyBuilder.activeSchedules();
        return redisTemplate.opsForZSet()
                .range(key, Range.closed(0L, -1L))
                .map(Object::toString);
    }
}
