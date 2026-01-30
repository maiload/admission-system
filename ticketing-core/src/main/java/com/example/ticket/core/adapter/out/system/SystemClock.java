package com.example.ticket.core.adapter.out.system;

import com.example.ticket.common.port.ClockPort;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
public class SystemClock implements ClockPort {

    @Override
    public Instant now() {
        return Instant.now();
    }

    @Override
    public long nowMillis() {
        return System.currentTimeMillis();
    }

    @Override
    public long nowEpochSecond() {
        return Instant.now().getEpochSecond();
    }
}
