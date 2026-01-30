package com.example.ticket.admission.adapter.out.system;

import com.example.ticket.common.port.ClockPort;

import java.time.Instant;

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
