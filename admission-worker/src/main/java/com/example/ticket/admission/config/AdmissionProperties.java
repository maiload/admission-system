package com.example.ticket.admission.config;

import com.example.ticket.admission.domain.AdmissionConfig;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "admission")
public record AdmissionProperties(
        Worker worker,
        Token token,
        Queue queue
) {
    public record Worker(int pollIntervalMs, int maxBatch, int rateCap, int concurrencyCap) { }

    public record Token(String secret, int ttlSec, int rateCounterTtlSec) { }

    public record Queue(int stateTtlSec) { }

    public AdmissionConfig toConfig() {
        return new AdmissionConfig(
                worker.maxBatch(),
                worker.rateCap(),
                worker.concurrencyCap(),
                token.ttlSec(),
                queue.stateTtlSec(),
                token.rateCounterTtlSec()
        );
    }
}
