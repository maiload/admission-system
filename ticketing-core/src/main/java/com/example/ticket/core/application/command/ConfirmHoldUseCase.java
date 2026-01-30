package com.example.ticket.core.application.command;

import com.example.ticket.common.error.BusinessException;
import com.example.ticket.common.error.ErrorCode;
import com.example.ticket.common.port.ClockPort;
import com.example.ticket.common.port.IdGeneratorPort;
import com.example.ticket.core.application.dto.command.ConfirmResult;
import com.example.ticket.core.application.port.out.*;
import com.example.ticket.core.domain.hold.Hold;
import com.example.ticket.core.domain.reservation.Reservation;
import com.example.ticket.core.domain.service.SeatAvailabilityService;
import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.UUID;

@RequiredArgsConstructor
public class ConfirmHoldUseCase {

    private final HoldRepositoryPort holdRepository;
    private final ReservationRepositoryPort reservationRepository;
    private final SeatQueryPort seatQuery;
    private final SessionPort sessionPort;
    private final SoldOutPort soldOutPort;
    private final SeatAvailabilityService availabilityService;
    private final ClockPort clock;
    private final IdGeneratorPort idGenerator;
    private final String eventId;
    private final long soldOutTtlSec;

    public Mono<ConfirmResult> execute(String clientId, UUID holdId,
                                       String scheduleId, String sessionId) {
        Instant now = clock.now();

        return holdRepository.findById(holdId)
                .switchIfEmpty(Mono.error(new BusinessException(ErrorCode.NOT_FOUND, "Hold not found")))
                .flatMap(hold -> {
                    // Domain validation
                    hold.validateConfirmable(clientId, now);

                    // Check idempotency: already confirmed?
                    return reservationRepository.findByScheduleAndSeat(hold.getScheduleId(), hold.getSeatId())
                            .flatMap(existing -> buildConfirmResult(existing, hold))
                            .switchIfEmpty(Mono.defer(() -> confirmAndCleanup(hold, clientId, scheduleId, sessionId)));
                });
    }

    private Mono<ConfirmResult> confirmAndCleanup(Hold hold, String clientId,
                                                   String scheduleId, String sessionId) {
        Instant now = clock.now();
        Reservation reservation = new Reservation(
                UUID.fromString(idGenerator.generateUuid()),
                hold.getScheduleId(),
                hold.getSeatId(),
                clientId,
                now
        );

        return reservationRepository.save(reservation)
                .flatMap(saved ->
                        holdRepository.deleteById(hold.getId())
                                .then(sessionPort.closeSession(eventId, scheduleId, sessionId, clientId))
                                .then(checkAndMarkSoldOut(hold.getScheduleId()))
                                .then(buildConfirmResult(saved, hold))
                );
    }

    private Mono<ConfirmResult> buildConfirmResult(Reservation reservation, Hold hold) {
        return seatQuery.findAllBySchedule(UUID.fromString(eventId), hold.getScheduleId())
                .filter(sv -> sv.getSeatId().equals(hold.getSeatId()))
                .next()
                .map(sv -> new ConfirmResult(
                        reservation.getId(),
                        hold.getScheduleId().toString(),
                        hold.getSeatId(),
                        sv.getZone(),
                        sv.getSeatNo(),
                        reservation.getConfirmedAt().toEpochMilli()
                ))
                .switchIfEmpty(Mono.just(new ConfirmResult(
                        reservation.getId(),
                        hold.getScheduleId().toString(),
                        hold.getSeatId(),
                        "",
                        0,
                        reservation.getConfirmedAt().toEpochMilli()
                )));
    }

    private Mono<Void> checkAndMarkSoldOut(UUID scheduleId) {
        return Mono.zip(
                seatQuery.countTotalSeats(UUID.fromString(eventId)),
                seatQuery.countHeldSeats(scheduleId),
                seatQuery.countConfirmedSeats(scheduleId)
        ).flatMap(tuple -> {
            if (availabilityService.isSoldOut(tuple.getT1(), tuple.getT2(), tuple.getT3())) {
                return soldOutPort.markSoldOut(eventId, scheduleId.toString(), soldOutTtlSec);
            }
            return Mono.empty();
        });
    }
}
