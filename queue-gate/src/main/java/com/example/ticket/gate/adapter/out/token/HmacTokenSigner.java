package com.example.ticket.gate.adapter.out.token;

import com.example.ticket.common.token.HmacSigner;
import com.example.ticket.gate.application.port.out.TokenSignerPort;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class HmacTokenSigner implements TokenSignerPort {

    private final String syncTokenSecret;

    @Override
    public String signSyncToken(String payload) {
        return HmacSigner.createToken(payload, syncTokenSecret);
    }

    @Override
    public String verifySyncToken(String token) {
        return HmacSigner.verifyAndExtract(token, syncTokenSecret);
    }
}
