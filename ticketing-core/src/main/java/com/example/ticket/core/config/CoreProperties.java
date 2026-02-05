package com.example.ticket.core.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "core")
public record CoreProperties(
        String eventId,
        Session session,
        EnterToken enterToken,
        Hold hold,
        SoldOut soldout
) {
    public record Session(String secret, int ttlSec, String cookieName) { }

    public record EnterToken(String secret) { }

    public record Hold(int ttlSec, int maxPerClient) { }

    public record SoldOut(long ttlSec) { }
}
