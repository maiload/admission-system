package com.example.ticket.gate.adapter.out.redis;

import com.example.ticket.common.key.RedisKeyBuilder;
import com.example.ticket.common.port.ClockPort;
import com.example.ticket.gate.application.port.out.ScheduleQueryPort;
import com.example.ticket.gate.config.GateProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import reactor.core.publisher.Mono;

@RequiredArgsConstructor
public class RedisScheduleQuery implements ScheduleQueryPort {

    private final ReactiveRedisTemplate<String, String> redisTemplate;
    private final ClockPort clock;
    private final GateProperties gateProperties;

    @Override
    public Mono<Long> getStartAtMs(Query query) {
        var simulation = gateProperties.sync().simulation();
        if (simulation.enabled()) {
            return Mono.just(clock.nowMillis() + simulation.offsetMs());
        }
        String key = RedisKeyBuilder.activeSchedules();
        String member = query.eventId() + ":" + query.scheduleId();

        return redisTemplate.opsForZSet().score(key, member)
                .map(Double::longValue);
    }
}
