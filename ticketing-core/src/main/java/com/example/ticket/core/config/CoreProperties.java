package com.example.ticket.core.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "core")
public record CoreProperties(
        Session session,
        EnterToken enterToken,
        Test test,
        Hold hold,
        SoldOut soldout
) {
    public record Session(String secret, int ttlSec, String cookieName) { }

    public record EnterToken(String secret) { }

    public record Test(int loadTtlSec) { }

    public record Hold(int ttlSec, int maxPerClient) { }

    public record SoldOut(long ttlSec) { }
}
