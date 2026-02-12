package com.example.ticket.gate.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "gate")
public record GateProperties(
        Queue queue,
        Sse sse,
        Client client
) {
    public record Queue(int stateTtlSec, int refreshThresholdSec) { }

    public record Sse(int pushIntervalMs) { }

    public record Client(String cookieName, int cookieMaxAgeDays) { }


}
