package com.example.ticket.gate.application.port.out;

public interface TokenSignerPort {

    String signSyncToken(String payload);

    String verifySyncToken(String token);
}
