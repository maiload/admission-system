package com.example.ticket.gate.application.service;

import com.example.ticket.gate.application.port.in.StreamQueueInPort;
import com.example.ticket.gate.application.port.out.QueueRepositoryPort;
import com.example.ticket.gate.application.port.out.SoldOutQueryPort;
import com.example.ticket.gate.config.GateProperties;
import com.example.ticket.gate.domain.QueueStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;

@Service
@RequiredArgsConstructor
public class StreamQueueService implements StreamQueueInPort {

    private final QueueRepositoryPort queueRepositoryPort;
    private final SoldOutQueryPort soldOutQueryPort;
    private final GateProperties gateProperties;

    @Override
    public Flux<ProgressResult> stream(StreamQuery query) {
        int pushIntervalMs = gateProperties.sse().pushIntervalMs();
        return Flux.interval(Duration.ZERO, Duration.ofMillis(pushIntervalMs))
                .switchMap(tick -> buildProgress(query))
                .distinctUntilChanged()
                .takeUntil(this::isTerminalStatus);
    }

    @Override
    public Mono<ProgressResult> poll(StreamQuery query) {
        return buildProgress(query);
    }

    private Mono<ProgressResult> buildProgress(StreamQuery query) {
        String eventId = query.eventId();
        String scheduleId = query.scheduleId();
        String queueToken = query.queueToken();

        var stateQuery = toStateQuery(eventId, scheduleId, queueToken);
        var sizeQuery = toSizeQuery(eventId, scheduleId);
        var soldOutQuery = toSoldOutQuery(eventId, scheduleId);
        var ttlCommand = toStateTtlCommand(eventId, scheduleId, queueToken);

        Mono<ProgressResult> stateMono = queueRepositoryPort.getState(stateQuery);
        Mono<Long> sizeMono = queueRepositoryPort.getQueueSize(sizeQuery);
        Mono<Boolean> soldOutMono = soldOutQueryPort.isSoldOut(soldOutQuery);

        return Mono.zip(stateMono, sizeMono, soldOutMono)
                .map(tuple -> toProgressResult(tuple.getT1(), tuple.getT2(), tuple.getT3(), eventId, scheduleId))
                .flatMap(result -> refreshTtlThenReturnResult(result, ttlCommand))
                .defaultIfEmpty(new ProgressResult(
                        QueueStatus.EXPIRED, 0, 0,
                        null, eventId, scheduleId
                ));
    }

    private ProgressResult toProgressResult(ProgressResult state,
                                            long totalInQueue,
                                            boolean soldOut,
                                            String eventId,
                                            String scheduleId) {
        QueueStatus status = state.status();
        if (soldOut && status == QueueStatus.WAITING) {
            status = QueueStatus.SOLD_OUT;
        }

        return new ProgressResult(
                status, state.rank(), totalInQueue,
                state.enterToken(), eventId, scheduleId
        );
    }

    private boolean isTerminalStatus(ProgressResult result) {
        return result.status() != QueueStatus.WAITING;
    }

    private Mono<ProgressResult> refreshTtlThenReturnResult(
            ProgressResult result,
            QueueRepositoryPort.StateTtlCommand command
    ) {
        if (result.status() != QueueStatus.WAITING) {
            return Mono.just(result);
        }
        return queueRepositoryPort.refreshStateTtl(command)
                .thenReturn(result);
    }

    private QueueRepositoryPort.StateQuery toStateQuery(String eventId, String scheduleId, String queueToken) {
        return new QueueRepositoryPort.StateQuery(eventId, scheduleId, queueToken);
    }

    private QueueRepositoryPort.SizeQuery toSizeQuery(String eventId, String scheduleId) {
        return new QueueRepositoryPort.SizeQuery(eventId, scheduleId);
    }

    private SoldOutQueryPort.Query toSoldOutQuery(String eventId, String scheduleId) {
        return new SoldOutQueryPort.Query(eventId, scheduleId);
    }

    private QueueRepositoryPort.StateTtlCommand toStateTtlCommand(
            String eventId,
            String scheduleId,
            String queueToken
    ) {
        return new QueueRepositoryPort.StateTtlCommand(
                eventId,
                scheduleId,
                queueToken,
                gateProperties.queue().stateTtlSec(),
                gateProperties.queue().refreshThresholdSec()
        );
    }
}
