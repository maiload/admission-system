package com.example.ticket.core.adapter.out.token;

import com.example.ticket.common.token.HmacSigner;
import com.example.ticket.core.application.port.out.TokenSignerPort;
import com.example.ticket.core.config.CoreProperties;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class HmacTokenSigner implements TokenSignerPort {

    private final CoreProperties coreProperties;

    @Override
    public String signSessionToken(String payload) {
        return HmacSigner.createToken(payload, coreProperties.session().secret());
    }

    @Override
    public String verifySessionToken(String token) {
        return HmacSigner.verifyAndExtract(token, coreProperties.session().secret());
    }

    @Override
    public String verifyEnterToken(String token) {
        return HmacSigner.verifyAndExtract(token, coreProperties.enterToken().secret());
    }
}
