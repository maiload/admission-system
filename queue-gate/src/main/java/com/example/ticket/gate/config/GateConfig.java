package com.example.ticket.gate.config;

import com.example.ticket.common.port.ClockPort;
import com.example.ticket.common.port.IdGeneratorPort;
import com.example.ticket.gate.adapter.out.redis.RedisQueueRepository;
import com.example.ticket.gate.adapter.out.redis.RedisScheduleQuery;
import com.example.ticket.gate.adapter.out.redis.RedisSoldOutQuery;
import com.example.ticket.gate.adapter.out.token.HmacTokenSigner;
import com.example.ticket.gate.adapter.out.system.SystemClock;
import com.example.ticket.gate.adapter.out.system.UuidGenerator;
import com.example.ticket.gate.application.port.out.QueueRepositoryPort;
import com.example.ticket.gate.application.port.out.ScheduleQueryPort;
import com.example.ticket.gate.application.port.out.SoldOutQueryPort;
import com.example.ticket.gate.application.port.out.TokenSignerPort;
import com.example.ticket.gate.application.usecase.JoinQueueUseCase;
import com.example.ticket.gate.application.usecase.StreamQueueUseCase;
import com.example.ticket.gate.application.usecase.SyncUseCase;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;

import java.util.List;

@Configuration
public class GateConfig {

    // --- Infrastructure beans ---

    @Bean
    public ClockPort clockPort() {
        return new SystemClock();
    }

    @Bean
    public IdGeneratorPort idGeneratorPort() {
        return new UuidGenerator();
    }

    @Bean
    public TokenSignerPort tokenSignerPort(
            @Value("${gate.sync.token-secret}") String syncTokenSecret) {
        return new HmacTokenSigner(syncTokenSecret);
    }

    @Bean
    @SuppressWarnings("unchecked")
    public RedisScript<List> queueJoinScript() {
        RedisScript<List> script = RedisScript.of(new ClassPathResource("lua/queue-join.lua"), List.class);
        return script;
    }

    // --- Repository / Query beans ---

    @Bean
    public QueueRepositoryPort queueRepositoryPort(
            ReactiveRedisTemplate<String, String> redisTemplate,
            RedisScript<List> queueJoinScript) {
        return new RedisQueueRepository(redisTemplate, queueJoinScript);
    }

    @Bean
    public SoldOutQueryPort soldOutQueryPort(ReactiveRedisTemplate<String, String> redisTemplate) {
        return new RedisSoldOutQuery(redisTemplate);
    }

    @Bean
    public ScheduleQueryPort scheduleQueryPort(ReactiveRedisTemplate<String, String> redisTemplate) {
        return new RedisScheduleQuery(redisTemplate);
    }

    // --- UseCase beans ---

    @Bean
    public SyncUseCase syncUseCase(
            ScheduleQueryPort scheduleQueryPort,
            TokenSignerPort tokenSignerPort,
            ClockPort clockPort,
            @Value("${gate.sync.window-ms}") int windowMs) {
        return new SyncUseCase(scheduleQueryPort, tokenSignerPort, clockPort, windowMs);
    }

    @Bean
    public JoinQueueUseCase joinQueueUseCase(
            QueueRepositoryPort queueRepositoryPort,
            SoldOutQueryPort soldOutQueryPort,
            TokenSignerPort tokenSignerPort,
            IdGeneratorPort idGeneratorPort,
            ClockPort clockPort,
            @Value("${gate.queue.state-ttl-sec}") int stateTtlSec,
            @Value("${gate.sync.join-window-before-ms}") long joinWindowBeforeMs,
            @Value("${gate.sync.join-window-after-ms}") long joinWindowAfterMs,
            @Value("${gate.sync.token-ttl-after-start-ms}") long tokenTtlAfterStartMs) {
        return new JoinQueueUseCase(
                queueRepositoryPort, soldOutQueryPort, tokenSignerPort,
                idGeneratorPort, clockPort,
                stateTtlSec, joinWindowBeforeMs, joinWindowAfterMs, tokenTtlAfterStartMs);
    }

    @Bean
    public StreamQueueUseCase streamQueueUseCase(
            QueueRepositoryPort queueRepositoryPort,
            SoldOutQueryPort soldOutQueryPort,
            ClockPort clockPort,
            @Value("${gate.sse.push-interval-ms}") int pushIntervalMs) {
        return new StreamQueueUseCase(queueRepositoryPort, soldOutQueryPort, clockPort, pushIntervalMs);
    }
}
