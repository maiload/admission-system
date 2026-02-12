package com.example.ticket.core.application.service;

import com.example.ticket.common.error.BusinessException;
import com.example.ticket.common.error.ErrorCode;
import com.example.ticket.common.port.ClockPort;
import com.example.ticket.common.port.IdGeneratorPort;
import com.example.ticket.core.application.port.in.HoldInPort;
import com.example.ticket.core.application.port.out.HoldRepositoryPort;
import com.example.ticket.core.application.port.out.ReservationRepositoryPort;
import com.example.ticket.core.application.port.out.SeatQueryPort;
import com.example.ticket.core.application.port.out.SessionPort;
import com.example.ticket.core.application.port.out.SoldOutPort;
import com.example.ticket.core.domain.Hold;
import com.example.ticket.core.domain.Reservation;
import com.example.ticket.core.config.CoreProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.reactive.TransactionalOperator;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class HoldService implements HoldInPort {

    private final HoldRepositoryPort holdRepositoryPort;
    private final ReservationRepositoryPort reservationRepositoryPort;
    private final SeatQueryPort seatQueryPort;
    private final SessionPort sessionPort;
    private final SoldOutPort soldOutPort;
    private final ClockPort clockPort;
    private final IdGeneratorPort idGenerator;
    private final CoreProperties coreProperties;
    private final TransactionalOperator transactionalOperator;

    @Override
    public Mono<CreateHoldResult> createHold(CreateHoldCommand command) {
        int maxPerClient = coreProperties.hold().maxPerClient();
        int requestedCount = command.seatIds().size();

        if (requestedCount > maxPerClient) {
            return Mono.error(new BusinessException(ErrorCode.HOLD_LIMIT_EXCEEDED));
        }
        return saveHoldsAtomically(command);
    }

    @Override
    public Mono<ConfirmHoldResult> confirmHold(ConfirmHoldCommand command) {
        Instant now = clockPort.now();

        return holdRepositoryPort.findByGroupId(command.holdGroupId())
                .collectList()
                .flatMap(holds -> {
                    if (holds.isEmpty()) {
                        return Mono.error(new BusinessException(ErrorCode.NOT_FOUND, "Hold group not found"));
                    }
                    holds.forEach(hold -> hold.validateConfirmable(command.clientId(), now));
                    return saveReservationsAndCleanup(holds, command);
                });
    }

    private Mono<CreateHoldResult> saveHoldsAtomically(CreateHoldCommand command) {
        int holdTtlSec = coreProperties.hold().ttlSec();
        Instant now = clockPort.now();
        UUID holdGroupId = UUID.fromString(idGenerator.generateUuid());

        List<Hold> holds = command.seatIds().stream()
                .map(seatId -> Hold.create(
                        UUID.fromString(idGenerator.generateUuid()),
                        holdGroupId,
                        command.scheduleId(),
                        seatId,
                        command.clientId(),
                        now,
                        holdTtlSec
                ))
                .toList();

        Flux<Hold> saveAll = Flux.fromIterable(holds)
                .concatMap(holdRepositoryPort::save);

        return transactionalOperator.transactional(saveAll)
                .collectList()
                .flatMap(savedHolds -> checkAndMarkSoldOut(command.eventId(), command.scheduleId())
                        .then(buildCreateResult(savedHolds, command, holdGroupId, holdTtlSec)))
                .onErrorMap(DataIntegrityViolationException.class,
                        ex -> new BusinessException(ErrorCode.SEAT_ALREADY_HELD));
    }

    private Mono<CreateHoldResult> buildCreateResult(List<Hold> savedHolds, CreateHoldCommand command,
                                                      UUID holdGroupId, int holdTtlSec) {
        return seatQueryPort.findAllBySchedule(command.eventId(), command.scheduleId())
                .collectList()
                .map(allSeats -> {
                    List<HeldSeat> heldSeats = savedHolds.stream()
                            .map(hold -> {
                                var seatView = allSeats.stream()
                                        .filter(sv -> sv.seatId().equals(hold.seatId()))
                                        .findFirst()
                                        .orElse(null);
                                return new HeldSeat(
                                        hold.seatId(),
                                        seatView != null ? seatView.seatNo() : 0,
                                        seatView != null ? seatView.zone() : ""
                                );
                            })
                            .toList();
                    return new CreateHoldResult(
                            holdGroupId,
                            command.scheduleId(),
                            heldSeats,
                            savedHolds.getFirst().expiresAt().toEpochMilli(),
                            holdTtlSec
                    );
                });
    }

    private Mono<ConfirmHoldResult> saveReservationsAndCleanup(List<Hold> holds, ConfirmHoldCommand command) {
        Instant now = clockPort.now();

        Flux<Reservation> saveAll = Flux.fromIterable(holds)
                .concatMap(hold -> {
                    Reservation reservation = new Reservation(
                            UUID.fromString(idGenerator.generateUuid()),
                            hold.scheduleId(),
                            hold.seatId(),
                            command.clientId(),
                            now
                    );
                    return reservationRepositoryPort.save(reservation);
                });

        return transactionalOperator.transactional(saveAll)
                .collectList()
                .flatMap(savedReservations ->
                        holdRepositoryPort.deleteByGroupId(command.holdGroupId())
                                .then(sessionPort.closeSession(toCloseCommand(command)))
                                .then(checkAndMarkSoldOut(command.eventId(), holds.getFirst().scheduleId()))
                                .then(buildConfirmResult(savedReservations, command))
                );
    }

    private Mono<ConfirmHoldResult> buildConfirmResult(List<Reservation> reservations,
                                                       ConfirmHoldCommand command) {
        return seatQueryPort.findAllBySchedule(command.eventId(), command.scheduleId())
                .collectList()
                .map(allSeats -> {
                    List<ConfirmedSeat> confirmedSeats = reservations.stream()
                            .map(reservation -> {
                                var seatView = allSeats.stream()
                                        .filter(sv -> sv.seatId().equals(reservation.seatId()))
                                        .findFirst()
                                        .orElse(null);
                                return new ConfirmedSeat(
                                        reservation.id(),
                                        reservation.seatId(),
                                        seatView != null ? seatView.zone() : "",
                                        seatView != null ? seatView.seatNo() : 0
                                );
                            })
                            .toList();
                    return new ConfirmHoldResult(
                            command.scheduleId(),
                            confirmedSeats,
                            reservations.getFirst().confirmedAt().toEpochMilli()
                    );
                });
    }

    private Mono<Void> checkAndMarkSoldOut(UUID eventId, UUID scheduleId) {
        long ttlSec = coreProperties.soldout().ttlSec();

        return Mono.zip(
                        seatQueryPort.countTotalSeats(eventId),
                        seatQueryPort.countHeldSeats(scheduleId),
                        seatQueryPort.countConfirmedSeats(scheduleId)
                )
                .filter(t -> isAllSeatsTaken(t.getT1(), t.getT2(), t.getT3()))
                .flatMap(t -> soldOutPort.markSoldOut(eventId.toString(), scheduleId.toString(), ttlSec))
                .then();
    }

    private SessionPort.CloseCommand toCloseCommand(ConfirmHoldCommand command) {
        return new SessionPort.CloseCommand(
                command.eventId().toString(),
                command.scheduleId().toString(),
                command.sessionId(),
                command.clientId()
        );
    }

    private boolean isAllSeatsTaken(long totalSeats, long heldCount, long confirmedCount) {
        return (heldCount + confirmedCount) >= totalSeats;
    }
}
