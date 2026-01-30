package com.example.ticket.gate.application.usecase;

import com.example.ticket.common.error.BusinessException;
import com.example.ticket.common.error.ErrorCode;
import com.example.ticket.common.port.ClockPort;
import com.example.ticket.common.port.IdGeneratorPort;
import com.example.ticket.common.util.TokenFormat;
import com.example.ticket.gate.application.dto.JoinResult;
import com.example.ticket.gate.application.port.out.QueueRepositoryPort;
import com.example.ticket.gate.application.port.out.SoldOutQueryPort;
import com.example.ticket.gate.application.port.out.TokenSignerPort;
import com.example.ticket.gate.domain.RankEstimator;
import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Mono;

@RequiredArgsConstructor
public class JoinQueueUseCase {

    private final QueueRepositoryPort queueRepository;
    private final SoldOutQueryPort soldOutQuery;
    private final TokenSignerPort tokenSigner;
    private final IdGeneratorPort idGenerator;
    private final ClockPort clock;
    private final int stateTtlSec;
    private final long joinWindowBeforeMs;
    private final long joinWindowAfterMs;
    private final long tokenTtlAfterStartMs;

    public Mono<JoinResult> execute(String eventId, String scheduleId,
                                    String syncToken, String clientId) {
        // 1. Verify syncToken
        String payload = tokenSigner.verifySyncToken(syncToken);
        if (payload == null) {
            return Mono.error(new BusinessException(ErrorCode.INVALID_SYNC_TOKEN));
        }

        String[] claims = TokenFormat.splitClaims(payload);
        if (claims.length < 4) {
            return Mono.error(new BusinessException(ErrorCode.INVALID_SYNC_TOKEN));
        }

        String tokenEventId = claims[0];
        String tokenScheduleId = claims[1];
        long startAtMs = Long.parseLong(claims[2]);
        long issuedAtMs = Long.parseLong(claims[3]);

        // Validate eventId/scheduleId match
        if (!tokenEventId.equals(eventId) || !tokenScheduleId.equals(scheduleId)) {
            return Mono.error(new BusinessException(ErrorCode.INVALID_SYNC_TOKEN));
        }

        // Validate token not expired (issuedAt must be within startAt + ttl)
        if (issuedAtMs > startAtMs + tokenTtlAfterStartMs) {
            return Mono.error(new BusinessException(ErrorCode.INVALID_SYNC_TOKEN));
        }

        // 2. Validate join window
        long now = clock.nowMillis();
        long delta = now - startAtMs;

        if (delta < -joinWindowBeforeMs) {
            return Mono.error(new BusinessException(ErrorCode.TOO_EARLY));
        }
        if (delta > joinWindowAfterMs) {
            return Mono.error(new BusinessException(ErrorCode.INVALID_WINDOW));
        }

        // 3. Check sold out
        return soldOutQuery.isSoldOut(eventId, scheduleId)
                .flatMap(soldOut -> {
                    if (soldOut) {
                        return Mono.error(new BusinessException(ErrorCode.SOLD_OUT));
                    }

                    // 4. Estimate rank
                    long effectiveDelta = Math.max(0, delta);
                    long estimatedRank = RankEstimator.estimate(effectiveDelta);
                    double score = RankEstimator.computeScore(estimatedRank);

                    // 5. Generate queueToken
                    String queueToken = TokenFormat.queueToken(idGenerator.generateUuid());

                    // 6. Atomic join via Lua
                    return queueRepository.join(eventId, scheduleId, clientId,
                            queueToken, score, estimatedRank, stateTtlSec);
                });
    }
}
