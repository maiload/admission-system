package com.example.ticket.admission.config;

import com.example.ticket.common.port.ClockPort;
import com.example.ticket.common.port.IdGeneratorPort;
import com.example.ticket.admission.adapter.out.redis.RedisActiveSchedule;
import com.example.ticket.admission.adapter.out.redis.RedisIssuer;
import com.example.ticket.admission.adapter.out.system.UuidGenerator;
import com.example.ticket.admission.adapter.out.token.HmacTokenGenerator;
import com.example.ticket.admission.adapter.out.system.SystemClock;
import com.example.ticket.admission.application.job.AdmissionJob;
import com.example.ticket.admission.application.port.out.ActiveSchedulePort;
import com.example.ticket.admission.application.port.out.IssuerPort;
import com.example.ticket.admission.application.metrics.AdmissionMetrics;
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
    public RedisScript<List> admissionIssueScript() {
        return RedisScript.of(new ClassPathResource("lua/admission-issue.lua"), List.class);
    }

    // Outbound Port Beans

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
            AdmissionProperties admissionProperties,
            ClockPort clockPort,
            IdGeneratorPort idGeneratorPort) {
        return new HmacTokenGenerator(
                admissionProperties.token().secret(),
                admissionProperties.token().ttlSec(),
                clockPort,
                idGeneratorPort);
    }

    // Job Beans

    @Bean
    public AdmissionJob admissionJob(
            ActiveSchedulePort activeSchedulePort,
            IssuerPort issuerPort,
            TokenGeneratorPort tokenGeneratorPort,
            AdmissionProperties admissionProperties,
            AdmissionMetrics admissionMetrics) {
        return new AdmissionJob(
                activeSchedulePort,
                issuerPort,
                tokenGeneratorPort,
                admissionProperties.toConfig(),
                admissionMetrics);
    }
}
