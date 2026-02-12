package com.example.ticket.gate.application.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

@Component
public class GateMetrics {

    private final Counter joinCreatedCounter;

    public GateMetrics(MeterRegistry registry) {
        this.joinCreatedCounter = Counter.builder("gate_join_total")
                .description("Total join requests")
                .register(registry);
    }

    public void recordJoin() {
        joinCreatedCounter.increment();
    }
}
