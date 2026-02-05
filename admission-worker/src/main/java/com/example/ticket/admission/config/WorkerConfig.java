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
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.util.List;

@Configuration
@EnableScheduling
@EnableConfigurationProperties(AdmissionProperties.class)
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
            AdmissionProperties admissionProperties) {
        return new HmacTokenGenerator(
                admissionProperties.token().secret(),
                admissionProperties.token().ttlSec());
    }

    // --- Engine ---

    @Bean
    public AdmissionEngine admissionEngine(
            ActiveSchedulePort activeSchedulePort,
            IssuerPort issuerPort,
            TokenGeneratorPort tokenGeneratorPort,
            AdmissionProperties admissionProperties) {
        int maxBatch = admissionProperties.worker().maxBatch();
        int rateCap = admissionProperties.worker().rateCap();
        int concurrencyCap = admissionProperties.worker().concurrencyCap();
        int enterTtlSec = admissionProperties.token().ttlSec();
        int qstateTtlSec = admissionProperties.queue().stateTtlSec();
        int rateTtlSec = admissionProperties.token().rateCounterTtlSec();
        return new AdmissionEngine(
                activeSchedulePort, issuerPort, tokenGeneratorPort,
                maxBatch, rateCap, concurrencyCap,
                enterTtlSec, qstateTtlSec, rateTtlSec);
    }
}
