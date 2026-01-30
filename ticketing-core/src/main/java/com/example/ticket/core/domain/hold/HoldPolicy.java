package com.example.ticket.core.domain.hold;

import java.time.Instant;

public class HoldPolicy {

    private final int holdTtlSec;

    public HoldPolicy(int holdTtlSec) {
        this.holdTtlSec = holdTtlSec;
    }

    public Instant calculateExpiresAt(Instant now) {
        return now.plusSeconds(holdTtlSec);
    }

    public int getHoldTtlSec() {
        return holdTtlSec;
    }
}
