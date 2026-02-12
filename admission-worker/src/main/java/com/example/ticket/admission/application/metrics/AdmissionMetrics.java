package com.example.ticket.admission.application.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicLong;

@Component
public class AdmissionMetrics {

    private final Counter issuedCounter;
    private final AtomicLong queueRemaining;

    public AdmissionMetrics(MeterRegistry registry) {
        this.issuedCounter = Counter.builder("admission_issued_total")
                .description("Total admission tokens issued")
                .register(registry);
        this.queueRemaining = new AtomicLong(0);
        registry.gauge("admission_queue_remaining", queueRemaining);
    }

    public void recordIssued(long count) {
        issuedCounter.increment(count);
    }

    public void updateQueueRemaining(long remaining) {
        queueRemaining.set(remaining);
    }
}
