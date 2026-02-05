package com.example.ticket.core.application.service;

import com.example.ticket.common.error.BusinessException;
import com.example.ticket.common.error.ErrorCode;
import com.example.ticket.common.port.IdGeneratorPort;
import com.example.ticket.common.token.HmacSigner;
import com.example.ticket.common.util.TokenFormat;
import com.example.ticket.core.application.dto.command.EnterCommand;
import com.example.ticket.core.application.dto.command.EnterResult;
import com.example.ticket.core.application.port.in.EnterCoreInPort;
import com.example.ticket.core.application.port.out.SessionPort;
import com.example.ticket.core.config.CoreProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
@RequiredArgsConstructor
public class EnterCoreService implements EnterCoreInPort {

    private final SessionPort sessionPort;
    private final IdGeneratorPort idGenerator;
    private final CoreProperties coreProperties;

    @Override
    public Mono<EnterResult> execute(EnterCommand command) {
        // 1. Verify enterToken HMAC
        String payload = HmacSigner.verifyAndExtract(command.enterToken(), coreProperties.enterToken().secret());
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
        if (!tokenEventId.equals(command.eventId()) || !tokenScheduleId.equals(command.scheduleId())) {
            return Mono.error(new BusinessException(ErrorCode.ENTER_TOKEN_INVALID));
        }

        // 3. Generate sessionId
        String sessionId = idGenerator.generateUuid();

        // 4. Atomic handshake via Lua: DEL enter + SET session + SADD active
        return sessionPort.handshake(command.eventId(), command.scheduleId(), jti, /* clientId resolved in Lua */ "", sessionId)
                .switchIfEmpty(Mono.error(new BusinessException(ErrorCode.ENTER_TOKEN_INVALID)))
                .map(clientId -> {
                    // 5. Create signed coreSessionToken
                    int sessionTtlSec = coreProperties.session().ttlSec();
                    String sessionPayload = TokenFormat.joinClaims(
                            sessionId, command.eventId(), command.scheduleId(),
                            String.valueOf(System.currentTimeMillis() + sessionTtlSec * 1000L)
                    );
                    String coreSessionToken = HmacSigner.createToken(
                            sessionPayload, coreProperties.session().secret());

                    return new EnterResult(coreSessionToken, sessionTtlSec, command.eventId(), command.scheduleId());
                });
    }
}
