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
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.Instant;
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

    @Override
    public Mono<CreateHoldResult> createHold(CreateHoldCommand command) {
        return validateHoldLimit(command)
                .then(Mono.defer(() -> {
                    int holdTtlSec = coreProperties.hold().ttlSec();

                    return holdRepositoryPort.save(buildHold(command, holdTtlSec))
                            .flatMap(saved -> checkAndMarkSoldOut(command.scheduleId())
                                    .then(buildCreateResult(saved, command, holdTtlSec))
                            );
                }));
    }

    @Override
    public Mono<ConfirmHoldResult> confirmHold(ConfirmHoldCommand command) {
        Instant now = clockPort.now();

        return findHoldOrError(command.holdId())
                .doOnNext(hold -> hold.validateConfirmable(command.clientId(), now))
                .flatMap(hold -> findOrConfirmHold(hold, command));
    }

    private Mono<Void> validateHoldLimit(CreateHoldCommand command) {
        int maxPerClient = coreProperties.hold().maxPerClient();
        return holdRepositoryPort.countByScheduleAndClient(command.scheduleId(), command.clientId())
                .flatMap(count -> count >= maxPerClient
                        ? Mono.error(new BusinessException(ErrorCode.ALREADY_HOLDING))
                        : Mono.empty()
                );
    }

    private Hold buildHold(CreateHoldCommand command, int holdTtlSec) {
        Instant now = clockPort.now();
        return Hold.create(
                UUID.fromString(idGenerator.generateUuid()),
                command.scheduleId(),
                command.seatId(),
                command.clientId(),
                now,
                holdTtlSec
        );
    }

    private Mono<CreateHoldResult> buildCreateResult(Hold saved, CreateHoldCommand command, int holdTtlSec) {
        return seatQueryPort.findAllBySchedule(UUID.fromString(coreProperties.eventId()), command.scheduleId())
                .filter(sv -> sv.seatId().equals(command.seatId()))
                .next()
                .map(sv -> new CreateHoldResult(
                        saved.id().toString(),
                        command.scheduleId().toString(),
                        command.seatId().toString(),
                        sv.seatNo(),
                        sv.zone(),
                        saved.expiresAt().toEpochMilli(),
                        holdTtlSec
                ));
    }

    private Mono<Hold> findHoldOrError(UUID holdId) {
        return holdRepositoryPort.findById(holdId)
                .switchIfEmpty(Mono.error(new BusinessException(ErrorCode.NOT_FOUND, "Hold not found")));
    }

    private Mono<ConfirmHoldResult> findOrConfirmHold(Hold hold, ConfirmHoldCommand command) {
        return reservationRepositoryPort.findByScheduleAndSeat(hold.scheduleId(), hold.seatId())
                .flatMap(existing -> buildConfirmResult(existing, hold))
                .switchIfEmpty(Mono.defer(() -> confirmAndCleanup(hold, command)));
    }

    private Mono<ConfirmHoldResult> confirmAndCleanup(Hold hold, ConfirmHoldCommand command) {
        int maxPerClient = coreProperties.hold().maxPerClient();

        return reservationRepositoryPort.countByScheduleAndClient(hold.scheduleId(), command.clientId())
                .flatMap(count -> count >= maxPerClient
                        ? Mono.error(new BusinessException(ErrorCode.ALREADY_HOLDING))
                        : saveAndCleanupHold(hold, command)
                );
    }

    private Mono<ConfirmHoldResult> saveAndCleanupHold(Hold hold, ConfirmHoldCommand command) {
        return reservationRepositoryPort.save(createReservation(hold, command))
                .flatMap(saved ->
                        holdRepositoryPort.deleteById(hold.id())
                                .then(sessionPort.closeSession(toCloseCommand(command)))
                                .then(checkAndMarkSoldOut(hold.scheduleId()))
                                .then(buildConfirmResult(saved, hold))
                );
    }

    private Reservation createReservation(Hold hold, ConfirmHoldCommand command) {
        return new Reservation(
                UUID.fromString(idGenerator.generateUuid()),
                hold.scheduleId(),
                hold.seatId(),
                command.clientId(),
                clockPort.now()
        );
    }

    private Mono<ConfirmHoldResult> buildConfirmResult(Reservation reservation, Hold hold) {
        UUID eventId = UUID.fromString(coreProperties.eventId());
        return seatQueryPort.findByScheduleAndSeatId(eventId, hold.scheduleId(), hold.seatId())
                .map(sv -> toConfirmHoldResult(reservation, hold, sv.zone(), sv.seatNo()))
                .defaultIfEmpty(toConfirmHoldResult(reservation, hold, "", 0));
    }

    private Mono<Void> checkAndMarkSoldOut(UUID scheduleId) {
        UUID eventId = UUID.fromString(coreProperties.eventId());
        long ttlSec = coreProperties.soldout().ttlSec();

        return Mono.zip(
                        seatQueryPort.countTotalSeats(eventId),
                        seatQueryPort.countHeldSeats(scheduleId),
                        seatQueryPort.countConfirmedSeats(scheduleId)
                )
                .filter(t -> isAllSeatsTaken(t.getT1(), t.getT2(), t.getT3()))
                .flatMap(t -> soldOutPort.markSoldOut(coreProperties.eventId(), scheduleId.toString(), ttlSec))
                .then();
    }

    private ConfirmHoldResult toConfirmHoldResult(Reservation reservation, Hold hold, String zone, int seatNo) {
        return new ConfirmHoldResult(
                reservation.id(),
                hold.scheduleId().toString(),
                hold.seatId().toString(),
                zone,
                seatNo,
                reservation.confirmedAt().toEpochMilli()
        );
    }

    private SessionPort.CloseCommand toCloseCommand(ConfirmHoldCommand command) {
        return new SessionPort.CloseCommand(
                coreProperties.eventId(),
                command.scheduleId(),
                command.sessionId(),
                command.clientId()
        );
    }

    private boolean isAllSeatsTaken(long totalSeats, long heldCount, long confirmedCount) {
        return (heldCount + confirmedCount) >= totalSeats;
    }
}
