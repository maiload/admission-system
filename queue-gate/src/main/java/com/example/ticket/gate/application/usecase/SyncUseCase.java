package com.example.ticket.gate.application.usecase;

import com.example.ticket.common.error.BusinessException;
import com.example.ticket.common.error.ErrorCode;
import com.example.ticket.common.port.ClockPort;
import com.example.ticket.common.util.TokenFormat;
import com.example.ticket.gate.application.dto.SyncResult;
import com.example.ticket.gate.application.port.out.ScheduleQueryPort;
import com.example.ticket.gate.application.port.out.TokenSignerPort;
import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Mono;

@RequiredArgsConstructor
public class SyncUseCase {

    private final ScheduleQueryPort scheduleQueryPort;
    private final TokenSignerPort tokenSignerPort;
    private final ClockPort clock;
    private final int windowMs;

    public Mono<SyncResult> execute(String eventId, String scheduleId) {
        return scheduleQueryPort.getStartAtMs(eventId, scheduleId)
                .switchIfEmpty(Mono.error(new BusinessException(ErrorCode.EVENT_NOT_OPEN)))
                .map(startAtMs -> {
                    long serverTimeMs = clock.nowMillis();
                    // syncToken payload: eventId|scheduleId|startAtMs|issuedAtMs
                    String payload = TokenFormat.joinClaims(
                            eventId, scheduleId,
                            String.valueOf(startAtMs),
                            String.valueOf(serverTimeMs)
                    );
                    String syncToken = tokenSignerPort.signSyncToken(payload);
                    return new SyncResult(serverTimeMs, startAtMs, syncToken, windowMs);
                });
    }
}
