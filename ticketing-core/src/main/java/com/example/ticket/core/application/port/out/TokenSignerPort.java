package com.example.ticket.core.application.port.out;

public interface TokenSignerPort {

    String signSessionToken(String payload);

    String verifySessionToken(String token);

    String verifyEnterToken(String token);
}
