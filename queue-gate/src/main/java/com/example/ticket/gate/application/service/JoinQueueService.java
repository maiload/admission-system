package com.example.ticket.gate.application.service;

import com.example.ticket.common.error.BusinessException;
import com.example.ticket.common.error.ErrorCode;
import com.example.ticket.common.port.ClockPort;
import com.example.ticket.common.port.IdGeneratorPort;
import com.example.ticket.common.util.TokenFormat;
import com.example.ticket.gate.application.port.in.JoinQueueInPort;
import com.example.ticket.gate.application.port.out.QueueRepositoryPort;
import com.example.ticket.gate.application.port.out.SoldOutQueryPort;
import com.example.ticket.gate.application.port.out.TokenSignerPort;
import com.example.ticket.gate.config.GateProperties;
import com.example.ticket.gate.domain.RankEstimator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
@RequiredArgsConstructor
public class JoinQueueService implements JoinQueueInPort {

    private static final int SYNC_TOKEN_CLAIMS_MIN = 4;

    private final QueueRepositoryPort queueRepositoryPort;
    private final SoldOutQueryPort soldOutQueryPort;
    private final TokenSignerPort tokenSignerPort;
    private final IdGeneratorPort idGeneratorPort;
    private final ClockPort clockPort;
    private final GateProperties gateProperties;

    @Override
    public Mono<JoinResult> execute(Join join) {
        return Mono.defer(() -> {
                    JoinContext ctx = buildContextOrError(join);
                    return Mono.just(ctx);
                })
                .flatMap(this::ensureNotSoldOut)
                .flatMap(this::joinAtomically);
    }

    private JoinContext buildContextOrError(Join join) {
        String payload = tokenSignerPort.verifySyncToken(join.syncToken());
        if (payload == null) {
            throw new BusinessException(ErrorCode.INVALID_SYNC_TOKEN);
        }

        SyncClaims claims = parseClaimsOrError(payload);
        validateTokenMatchesOrError(claims, join);

        long now = clockPort.nowMillis();
        validateTokenTtlOrError(now, claims.startAtMs());

        long deltaMs = now - claims.startAtMs();
        validateJoinWindowOrError(deltaMs);

        long effectiveDeltaMs = Math.max(0L, deltaMs);
        long estimatedRank = RankEstimator.estimate(effectiveDeltaMs);

        String queueToken = TokenFormat.queueToken(idGeneratorPort.generateUuid());

        int stateTtlSec = gateProperties.queue().stateTtlSec();

        return new JoinContext(
                join,
                estimatedRank,
                queueToken,
                stateTtlSec
        );
    }

    private SyncClaims parseClaimsOrError(String payload) {
        String[] claims = TokenFormat.splitClaims(payload);

        if (claims.length < SYNC_TOKEN_CLAIMS_MIN) {
            throw new BusinessException(ErrorCode.INVALID_SYNC_TOKEN);
        }

        String tokenEventId = claims[0];
        String tokenScheduleId = claims[1];

        long startAtMs;
        try {
            startAtMs = Long.parseLong(claims[2]);
        } catch (NumberFormatException e) {
            throw new BusinessException(ErrorCode.INVALID_SYNC_TOKEN);
        }

        return new SyncClaims(tokenEventId, tokenScheduleId, startAtMs);
    }

    private void validateTokenMatchesOrError(SyncClaims claims, Join command) {
        if (!claims.eventId().equals(command.eventId()) ||
                !claims.scheduleId().equals(command.scheduleId())) {
            throw new BusinessException(ErrorCode.INVALID_SYNC_TOKEN);
        }
    }

    private void validateTokenTtlOrError(long now, long startAtMs) {
        long tokenTtlAfterStartMs = gateProperties.sync().tokenTtlAfterStartMs();
        if (now > startAtMs + tokenTtlAfterStartMs) {
            throw new BusinessException(ErrorCode.INVALID_SYNC_TOKEN);
        }
    }

    private void validateJoinWindowOrError(long deltaMs) {
        long afterMs = gateProperties.sync().joinWindowAfterMs();
        if (deltaMs < 0) {
            throw new BusinessException(ErrorCode.TOO_EARLY);
        }
        if (deltaMs > afterMs) {
            throw new BusinessException(ErrorCode.INVALID_WINDOW);
        }
    }

    private Mono<JoinContext> ensureNotSoldOut(JoinContext ctx) {
        return soldOutQueryPort.isSoldOut(ctx.toSoldOutQuery())
                .flatMap(soldOut -> Boolean.TRUE.equals(soldOut)
                        ? Mono.error(new BusinessException(ErrorCode.SOLD_OUT))
                        : Mono.just(ctx)
                );
    }

    private Mono<JoinResult> joinAtomically(JoinContext ctx) {
        return queueRepositoryPort.join(ctx.toQueueJoinCommand());
    }

    private record SyncClaims(String eventId, String scheduleId, long startAtMs) {}

    private record JoinContext(
            Join command,
            long estimatedRank,
            String queueToken,
            int stateTtlSec
    ) {
        private SoldOutQueryPort.Query toSoldOutQuery() {
            return new SoldOutQueryPort.Query(command.eventId(), command.scheduleId());
        }

        private QueueRepositoryPort.JoinCommand toQueueJoinCommand() {
            return new QueueRepositoryPort.JoinCommand(
                    command.eventId(),
                    command.scheduleId(),
                    command.clientId(),
                    queueToken,
                    estimatedRank,
                    stateTtlSec
            );
        }
    }
}
