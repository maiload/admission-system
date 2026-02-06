package com.example.ticket.core.adapter.out.redis;

import com.example.ticket.common.key.RedisKeyBuilder;
import com.example.ticket.core.application.port.out.ActiveScheduleReadPort;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Range;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

@Component
@RequiredArgsConstructor
public class RedisActiveScheduleReader implements ActiveScheduleReadPort {

    private static final int EXPECTED_PARTS = 2;

    private final ReactiveStringRedisTemplate redisTemplate;

    @Override
    public Flux<ActiveSchedule> findAll() {
        return redisTemplate.opsForZSet()
                .range(RedisKeyBuilder.activeSchedules(), Range.unbounded())
                .flatMap(this::parseMember);
    }

    private Flux<ActiveSchedule> parseMember(String member) {
        String[] parts = member.split(":", EXPECTED_PARTS);
        if (parts.length != EXPECTED_PARTS) {
            return Flux.empty();
        }
        return Flux.just(new ActiveSchedule(parts[0], parts[1]));
    }
}
