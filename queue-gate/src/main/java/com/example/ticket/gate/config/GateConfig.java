package com.example.ticket.gate.config;

import com.example.ticket.common.port.ClockPort;
import com.example.ticket.common.port.IdGeneratorPort;
import com.example.ticket.gate.adapter.out.redis.RedisQueueAdapter;
import com.example.ticket.gate.adapter.out.redis.RedisScheduleQuery;
import com.example.ticket.gate.adapter.out.redis.RedisSoldOutQuery;
import com.example.ticket.gate.adapter.out.system.SystemClock;
import com.example.ticket.gate.adapter.out.system.UuidGenerator;
import com.example.ticket.gate.application.port.out.QueueRepositoryPort;
import com.example.ticket.gate.application.port.out.ScheduleQueryPort;
import com.example.ticket.gate.application.port.out.SoldOutQueryPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;

import java.util.List;

@Configuration
public class GateConfig {

    // Infra Beans

    @Bean
    public ClockPort clockPort() {
        return new SystemClock();
    }

    @Bean
    public IdGeneratorPort idGeneratorPort() {
        return new UuidGenerator();
    }

    @Bean
    @SuppressWarnings("unchecked")
    public RedisScript<List> queueJoinScript() {
         return RedisScript.of(new ClassPathResource("lua/queue-join.lua"), List.class);
    }

    // Outbound Port Beans

    @Bean
    public QueueRepositoryPort queueRepositoryPort(
            ReactiveRedisTemplate<String, String> redisTemplate,
            RedisScript<List> queueJoinScript) {
        return new RedisQueueAdapter(redisTemplate, queueJoinScript);
    }

    @Bean
    public SoldOutQueryPort soldOutQueryPort(ReactiveRedisTemplate<String, String> redisTemplate) {
        return new RedisSoldOutQuery(redisTemplate);
    }

    @Bean
    public ScheduleQueryPort scheduleQueryPort(ReactiveRedisTemplate<String, String> redisTemplate) {
        return new RedisScheduleQuery(redisTemplate);
    }
}
