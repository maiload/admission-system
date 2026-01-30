package com.example.ticket.admission.config;

import com.example.ticket.common.port.ClockPort;
import com.example.ticket.admission.adapter.out.redis.RedisActiveSchedule;
import com.example.ticket.admission.adapter.out.redis.RedisIssuer;
import com.example.ticket.admission.adapter.out.token.HmacTokenGenerator;
import com.example.ticket.admission.adapter.out.system.SystemClock;
import com.example.ticket.admission.application.engine.AdmissionEngine;
import com.example.ticket.admission.application.port.out.ActiveSchedulePort;
import com.example.ticket.admission.application.port.out.IssuerPort;
import com.example.ticket.admission.application.port.out.TokenGeneratorPort;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.util.List;

@Configuration
@EnableScheduling
public class WorkerConfig {

    // --- Infrastructure ---

    @Bean
    public ClockPort clockPort() {
        return new SystemClock();
    }

    @Bean
    @SuppressWarnings("unchecked")
    public RedisScript<List> admissionIssueScript() {
        return RedisScript.of(new ClassPathResource("lua/admission-issue.lua"), List.class);
    }

    // --- Ports ---

    @Bean
    public IssuerPort issuerPort(
            ReactiveRedisTemplate<String, String> redisTemplate,
            RedisScript<List> admissionIssueScript,
            ClockPort clockPort) {
        return new RedisIssuer(redisTemplate, admissionIssueScript, clockPort);
    }

    @Bean
    public ActiveSchedulePort activeSchedulePort(
            ReactiveRedisTemplate<String, String> redisTemplate) {
        return new RedisActiveSchedule(redisTemplate);
    }

    @Bean
    public TokenGeneratorPort tokenGeneratorPort(
            @Value("${admission.token.secret}") String enterTokenSecret,
            @Value("${admission.token.ttl-sec}") int enterTtlSec) {
        return new HmacTokenGenerator(enterTokenSecret, enterTtlSec);
    }

    // --- Engine ---

    @Bean
    public AdmissionEngine admissionEngine(
            ActiveSchedulePort activeSchedulePort,
            IssuerPort issuerPort,
            TokenGeneratorPort tokenGeneratorPort,
            @Value("${admission.worker.max-batch}") int maxBatch,
            @Value("${admission.worker.rate-cap}") int rateCap,
            @Value("${admission.worker.concurrency-cap}") int concurrencyCap,
            @Value("${admission.token.ttl-sec}") int enterTtlSec,
            @Value("${gate.queue.state-ttl-sec:1800}") int qstateTtlSec,
            @Value("${admission.token.rate-counter-ttl-sec}") int rateTtlSec) {
        return new AdmissionEngine(
                activeSchedulePort, issuerPort, tokenGeneratorPort,
                maxBatch, rateCap, concurrencyCap,
                enterTtlSec, qstateTtlSec, rateTtlSec);
    }
}
