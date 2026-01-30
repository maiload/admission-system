package com.example.ticket.core.application.command;

import com.example.ticket.common.error.BusinessException;
import com.example.ticket.common.error.ErrorCode;
import com.example.ticket.common.port.IdGeneratorPort;
import com.example.ticket.common.token.HmacSigner;
import com.example.ticket.common.util.TokenFormat;
import com.example.ticket.core.application.dto.command.EnterResult;
import com.example.ticket.core.application.port.out.SessionPort;
import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Mono;

@RequiredArgsConstructor
public class EnterCoreUseCase {

    private final SessionPort sessionPort;
    private final IdGeneratorPort idGenerator;
    private final String enterTokenSecret;
    private final String coreSessionSecret;
    private final int sessionTtlSec;

    public Mono<EnterResult> execute(String enterToken, String eventId, String scheduleId) {
        // 1. Verify enterToken HMAC
        String payload = HmacSigner.verifyAndExtract(enterToken, enterTokenSecret);
        if (payload == null) {
            return Mono.error(new BusinessException(ErrorCode.ENTER_TOKEN_INVALID));
        }

        String[] claims = TokenFormat.splitClaims(payload);
        if (claims.length < 4) {
            return Mono.error(new BusinessException(ErrorCode.ENTER_TOKEN_INVALID));
        }

        String jti = claims[0];
        String tokenEventId = claims[1];
        String tokenScheduleId = claims[2];

        // 2. Validate eventId/scheduleId match
        if (!tokenEventId.equals(eventId) || !tokenScheduleId.equals(scheduleId)) {
            return Mono.error(new BusinessException(ErrorCode.ENTER_TOKEN_INVALID));
        }

        // 3. Generate sessionId
        String sessionId = idGenerator.generateUuid();

        // 4. Atomic handshake via Lua: DEL enter + SET session + SADD active
        return sessionPort.handshake(eventId, scheduleId, jti, /* clientId resolved in Lua */ "", sessionId)
                .switchIfEmpty(Mono.error(new BusinessException(ErrorCode.ENTER_TOKEN_INVALID)))
                .map(clientId -> {
                    // 5. Create signed coreSessionToken
                    String sessionPayload = TokenFormat.joinClaims(
                            sessionId, eventId, scheduleId, String.valueOf(System.currentTimeMillis() + sessionTtlSec * 1000L)
                    );
                    String coreSessionToken = HmacSigner.createToken(sessionPayload, coreSessionSecret);

                    return new EnterResult(coreSessionToken, sessionTtlSec, eventId, scheduleId);
                });
    }
}
