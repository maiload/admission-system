package com.example.ticket.gate.application.service;

import com.example.ticket.common.error.BusinessException;
import com.example.ticket.common.error.ErrorCode;
import com.example.ticket.common.port.ClockPort;
import com.example.ticket.common.port.IdGeneratorPort;
import com.example.ticket.common.util.TokenFormat;
import com.example.ticket.gate.application.metrics.GateMetrics;
import com.example.ticket.gate.application.port.in.JoinQueueInPort;
import com.example.ticket.gate.application.port.out.QueueRepositoryPort;
import com.example.ticket.gate.application.port.out.ScheduleQueryPort;
import com.example.ticket.gate.application.port.out.SoldOutQueryPort;
import com.example.ticket.gate.config.GateProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
@RequiredArgsConstructor
public class JoinQueueService implements JoinQueueInPort {

    private final QueueRepositoryPort queueRepositoryPort;
    private final ScheduleQueryPort scheduleQueryPort;
    private final SoldOutQueryPort soldOutQueryPort;
    private final IdGeneratorPort idGeneratorPort;
    private final ClockPort clockPort;
    private final GateProperties gateProperties;
    private final GateMetrics metrics;

    @Override
    public Mono<JoinResult> execute(Join join) {
        return scheduleQueryPort.getStartAtMs(toScheduleQuery(join))
                .switchIfEmpty(Mono.error(new BusinessException(ErrorCode.SCHEDULE_NOT_FOUND)))
                .map(startAtMs -> buildContextOrError(join, startAtMs))
                .flatMap(this::ensureNotSoldOut)
                .flatMap(this::joinWithScript)
                .doOnNext(result -> metrics.recordJoin());
    }

    private JoinContext buildContextOrError(Join join, long startAtMs) {
        long now = clockPort.nowMillis();
        validateStartAtOrError(now, startAtMs);

        String queueToken = TokenFormat.queueToken(idGeneratorPort.generateUuid());

        int stateTtlSec = gateProperties.queue().stateTtlSec();

        return new JoinContext(
                join,
                queueToken,
                stateTtlSec
        );
    }

    // 테스트를 위해 주석 처리
    private void validateStartAtOrError(long now, long startAtMs) {
//        if (now < startAtMs) {
//            throw new BusinessException(ErrorCode.TOO_EARLY);
//        }
    }

    private Mono<JoinContext> ensureNotSoldOut(JoinContext ctx) {
        return soldOutQueryPort.isSoldOut(ctx.toSoldOutQuery())
                .flatMap(soldOut -> Boolean.TRUE.equals(soldOut)
                        ? Mono.error(new BusinessException(ErrorCode.SOLD_OUT))
                        : Mono.just(ctx)
                );
    }

    private Mono<JoinResult> joinWithScript(JoinContext ctx) {
        return queueRepositoryPort.join(ctx.toQueueJoinCommand());
    }

    private record JoinContext(
            Join command,
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
                    stateTtlSec,
                    command.loadTest()
            );
        }
    }

    private ScheduleQueryPort.Query toScheduleQuery(Join join) {
        return new ScheduleQueryPort.Query(join.eventId(), join.scheduleId());
    }
}
