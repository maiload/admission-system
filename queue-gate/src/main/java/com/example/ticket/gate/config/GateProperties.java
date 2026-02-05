package com.example.ticket.gate.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "gate")
public record GateProperties(
        Sync sync,
        Queue queue,
        Sse sse,
        Client client
) {
    public record Sync(
            String tokenSecret,
            long joinWindowBeforeMs,
            long joinWindowAfterMs,
            long tokenTtlAfterStartMs,
            Simulation simulation
    ) {
        public record Simulation(boolean enabled, long offsetMs, long exitRatePerSec) { }
    }

    public record Queue(int stateTtlSec) { }

    public record Sse(int pushIntervalMs) { }

    public record Client(String cookieName, int cookieMaxAgeDays) { }


}
