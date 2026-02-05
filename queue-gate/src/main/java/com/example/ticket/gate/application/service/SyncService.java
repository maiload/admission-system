package com.example.ticket.gate.application.service;

import com.example.ticket.common.error.BusinessException;
import com.example.ticket.common.error.ErrorCode;
import com.example.ticket.common.port.ClockPort;
import com.example.ticket.common.util.TokenFormat;
import com.example.ticket.gate.application.port.in.SyncInPort;
import com.example.ticket.gate.application.port.out.ScheduleQueryPort;
import com.example.ticket.gate.application.port.out.TokenSignerPort;
import com.example.ticket.gate.config.GateProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
@RequiredArgsConstructor
public class SyncService implements SyncInPort {

    private final ScheduleQueryPort scheduleQueryPort;
    private final TokenSignerPort tokenSignerPort;
    private final ClockPort clock;
    private final GateProperties gateProperties;

    @Override
    public Mono<SyncResult> execute(Sync sync) {
        var scheduleQuery = new ScheduleQueryPort.Query(sync.eventId(), sync.scheduleId());
        return scheduleQueryPort.getStartAtMs(scheduleQuery)
                .switchIfEmpty(Mono.error(new BusinessException(ErrorCode.EVENT_NOT_OPEN)))
                .map(startAtMs -> {
                    long serverTimeMs = clock.nowMillis();
                    String payload = TokenFormat.joinClaims(
                            sync.eventId(), sync.scheduleId(),
                            String.valueOf(startAtMs),
                            String.valueOf(serverTimeMs)
                    );
                    String syncToken = tokenSignerPort.signSyncToken(payload);
                    return new SyncResult(serverTimeMs, startAtMs, syncToken);
                });
    }
}
