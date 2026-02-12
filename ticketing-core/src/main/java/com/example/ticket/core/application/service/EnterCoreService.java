package com.example.ticket.core.application.service;

import com.example.ticket.common.error.BusinessException;
import com.example.ticket.common.error.ErrorCode;
import com.example.ticket.common.port.ClockPort;
import com.example.ticket.common.port.IdGeneratorPort;
import com.example.ticket.common.util.TokenFormat;
import com.example.ticket.core.application.port.in.EnterCoreInPort.EnterCommand;
import com.example.ticket.core.application.port.in.EnterCoreInPort.EnterResult;
import com.example.ticket.core.application.port.in.EnterCoreInPort;
import com.example.ticket.core.application.port.out.SessionPort;
import com.example.ticket.core.application.port.out.TokenSignerPort;
import com.example.ticket.core.config.CoreProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class EnterCoreService implements EnterCoreInPort {

    private static final int ENTER_TOKEN_CLAIMS_MIN = 4;

    private final SessionPort sessionPort;
    private final IdGeneratorPort idGenerator;
    private final ClockPort clockPort;
    private final CoreProperties coreProperties;
    private final TokenSignerPort tokenSignerPort;

    @Override
    public Mono<EnterResult> execute(EnterCommand command) {
        return Mono.defer(() -> {
                    EnterContext ctx = buildContextOrError(command);
                    return Mono.just(ctx);
                })
                .flatMap(this::handshakeAndIssue);
    }

    private EnterContext buildContextOrError(EnterCommand command) {
        String payload = tokenSignerPort.verifyEnterToken(command.enterToken());
        if (payload == null) {
            throw new BusinessException(ErrorCode.ENTER_TOKEN_INVALID);
        }

        EnterClaims claims = parseClaimsOrError(payload);
        validateTokenMatchesOrError(claims, command);

        String sessionId = idGenerator.generateUuid();
        int sessionTtlSec = coreProperties.session().ttlSec();

        return new EnterContext(command.eventId(), command.scheduleId(), claims.jti(), sessionId, sessionTtlSec);
    }

    private Mono<EnterResult> handshakeAndIssue(EnterContext ctx) {
        return sessionPort.handshake(ctx.toHandshakeCommand())
                .switchIfEmpty(Mono.error(new BusinessException(ErrorCode.ENTER_TOKEN_INVALID)))
                .map(clientId -> toResult(ctx));
    }

    private EnterClaims parseClaimsOrError(String payload) {
        String[] claims = TokenFormat.splitClaims(payload);
        if (claims.length < ENTER_TOKEN_CLAIMS_MIN) {
            throw new BusinessException(ErrorCode.ENTER_TOKEN_INVALID);
        }
        return new EnterClaims(claims[0], claims[1], claims[2]);
    }

    private void validateTokenMatchesOrError(EnterClaims claims, EnterCommand command) {
        if (!claims.eventId().equals(command.eventId().toString()) ||
                !claims.scheduleId().equals(command.scheduleId().toString())) {
            throw new BusinessException(ErrorCode.ENTER_TOKEN_INVALID);
        }
    }

    private EnterResult toResult(EnterContext ctx) {
        long expMs = clockPort.nowMillis() + ctx.sessionTtlSec() * 1000L;
        String sessionPayload = TokenFormat.joinClaims(
                ctx.sessionId(),
                ctx.eventId().toString(),
                ctx.scheduleId().toString(),
                String.valueOf(expMs)
        );
        String coreSessionToken = tokenSignerPort.signSessionToken(sessionPayload);
        return new EnterResult(coreSessionToken, ctx.sessionTtlSec(), ctx.eventId(), ctx.scheduleId());
    }

    private record EnterClaims(String jti, String eventId, String scheduleId) {}

    private record EnterContext(
            UUID eventId,
            UUID scheduleId,
            String jti,
            String sessionId,
            int sessionTtlSec
    ) {
        private SessionPort.HandshakeCommand toHandshakeCommand() {
            return new SessionPort.HandshakeCommand(eventId.toString(), scheduleId.toString(), jti, sessionId);
        }
    }
}
