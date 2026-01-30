package com.example.ticket.common.port;

import java.time.Instant;

public interface ClockPort {

    Instant now();

    long nowMillis();

    long nowEpochSecond();
}
