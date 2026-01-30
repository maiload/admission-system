package com.example.ticket.core.application.command;

import com.example.ticket.common.error.BusinessException;
import com.example.ticket.common.error.ErrorCode;
import com.example.ticket.common.port.ClockPort;
import com.example.ticket.common.port.IdGeneratorPort;
import com.example.ticket.core.application.dto.command.HoldResult;
import com.example.ticket.core.application.port.out.HoldRepositoryPort;
import com.example.ticket.core.application.port.out.SeatQueryPort;
import com.example.ticket.core.application.port.out.SoldOutPort;
import com.example.ticket.core.domain.hold.Hold;
import com.example.ticket.core.domain.hold.HoldPolicy;
import com.example.ticket.core.domain.service.SeatAvailabilityService;
import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.UUID;

@RequiredArgsConstructor
public class CreateHoldUseCase {

    private final HoldRepositoryPort holdRepository;
    private final SeatQueryPort seatQuery;
    private final SoldOutPort soldOutPort;
    private final HoldPolicy holdPolicy;
    private final SeatAvailabilityService availabilityService;
    private final ClockPort clock;
    private final IdGeneratorPort idGenerator;
    private final String eventId;
    private final long soldOutTtlSec;

    public Mono<HoldResult> execute(String clientId, UUID scheduleId, UUID seatId) {
        Instant now = clock.now();

        // 1. Check if already holding another seat
        return holdRepository.findByScheduleAndClient(scheduleId, clientId)
                .flatMap(existing -> Mono.<HoldResult>error(new BusinessException(ErrorCode.ALREADY_HOLDING)))
                .switchIfEmpty(Mono.defer(() -> {
                    // 2. Create hold
                    Instant expiresAt = holdPolicy.calculateExpiresAt(now);
                    Hold hold = new Hold(
                            UUID.fromString(idGenerator.generateUuid()),
                            scheduleId,
                            seatId,
                            clientId,
                            expiresAt,
                            now
                    );

                    return holdRepository.save(hold)
                            .flatMap(saved -> {
                                // 3. Check sold out after hold
                                return checkAndMarkSoldOut(scheduleId)
                                        .then(seatQuery.findAllBySchedule(UUID.fromString(eventId), scheduleId)
                                                .filter(sv -> sv.getSeatId().equals(seatId))
                                                .next()
                                                .map(sv -> new HoldResult(
                                                        saved.getId(),
                                                        scheduleId.toString(),
                                                        seatId,
                                                        sv.getSeatNo(),
                                                        sv.getZone(),
                                                        expiresAt.toEpochMilli(),
                                                        holdPolicy.getHoldTtlSec()
                                                ))
                                        );
                            });
                }));
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
