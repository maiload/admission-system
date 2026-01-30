package com.example.ticket.admission.adapter.out.token;

import com.example.ticket.common.token.HmacSigner;
import com.example.ticket.common.util.TokenFormat;
import com.example.ticket.admission.application.port.out.TokenGeneratorPort;
import lombok.RequiredArgsConstructor;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@RequiredArgsConstructor
public class HmacTokenGenerator implements TokenGeneratorPort {

    private final String enterTokenSecret;
    private final int enterTtlSec;

    @Override
    public List<String> generateBatch(String eventId, String scheduleId, int count) {
        List<String> pairs = new ArrayList<>(count * 2);
        long expMs = System.currentTimeMillis() + enterTtlSec * 1000L;

        for (int i = 0; i < count; i++) {
            String jti = UUID.randomUUID().toString();
            // enterToken payload: jti|eventId|scheduleId|expMs
            String payload = TokenFormat.joinClaims(jti, eventId, scheduleId, String.valueOf(expMs));
            String token = HmacSigner.createToken(payload, enterTokenSecret);

            pairs.add(jti);
            pairs.add(token);
        }

        return pairs;
    }
}
