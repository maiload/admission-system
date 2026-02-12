package com.example.ticket.admission.adapter.out.token;

import com.example.ticket.common.token.HmacSigner;
import com.example.ticket.common.util.TokenFormat;
import com.example.ticket.common.port.ClockPort;
import com.example.ticket.common.port.IdGeneratorPort;
import com.example.ticket.admission.application.port.out.TokenGeneratorPort;
import lombok.RequiredArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@RequiredArgsConstructor
public class HmacTokenGenerator implements TokenGeneratorPort {

    private final String enterTokenSecret;
    private final int enterTtlSec;
    private final ClockPort clockPort;
    private final IdGeneratorPort idGeneratorPort;

    @Override
    public List<String> generateBatch(String eventId, String scheduleId, int count) {
        List<String> pairs = new ArrayList<>(count * 2);
        long expMs = clockPort.nowMillis() + enterTtlSec * 1000L;

        for (int i = 0; i < count; i++) {
            String jti = idGeneratorPort.generateUuid();
            // enterToken payload: jti|eventId|scheduleId|expMs
            String payload = TokenFormat.joinClaims(jti, eventId, scheduleId, String.valueOf(expMs));
            String token = HmacSigner.createToken(payload, enterTokenSecret);

            pairs.add(jti);
            pairs.add(token);
        }

        return pairs;
    }
}
