package com.example.ticket.core.application.service;

import com.example.ticket.common.error.BusinessException;
import com.example.ticket.common.error.ErrorCode;
import com.example.ticket.common.port.ClockPort;
import com.example.ticket.common.port.IdGeneratorPort;
import com.example.ticket.core.application.dto.command.CreateHoldCommand;
import com.example.ticket.core.application.dto.command.HoldResult;
import com.example.ticket.core.application.port.in.CreateHoldInPort;
import com.example.ticket.core.application.port.out.HoldRepositoryPort;
import com.example.ticket.core.application.port.out.SeatQueryPort;
import com.example.ticket.core.application.port.out.SoldOutPort;
import com.example.ticket.core.domain.hold.Hold;
import com.example.ticket.core.domain.hold.HoldPolicy;
import com.example.ticket.core.domain.service.SeatAvailabilityService;
import com.example.ticket.core.config.CoreProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CreateHoldService implements CreateHoldInPort {

    private final HoldRepositoryPort holdRepository;
    private final SeatQueryPort seatQuery;
    private final SoldOutPort soldOutPort;
    private final HoldPolicy holdPolicy;
    private final SeatAvailabilityService availabilityService;
    private final ClockPort clock;
    private final IdGeneratorPort idGenerator;
    private final CoreProperties coreProperties;

    @Override
    public Mono<HoldResult> execute(CreateHoldCommand command) {
        Instant now = clock.now();

        int maxPerClient = coreProperties.hold().maxPerClient();
        // 1. Check if client exceeded hold limit
        return holdRepository.countByScheduleAndClient(command.scheduleId(), command.clientId())
                .flatMap(count -> {
                    if (count >= maxPerClient) {
                        return Mono.<HoldResult>error(new BusinessException(ErrorCode.ALREADY_HOLDING));
                    }
                    return Mono.empty();
                })
                .switchIfEmpty(Mono.defer(() -> {
                    // 2. Create hold
                    Instant expiresAt = holdPolicy.calculateExpiresAt(now);
                    Hold hold = new Hold(
                            UUID.fromString(idGenerator.generateUuid()),
                            command.scheduleId(),
                            command.seatId(),
                            command.clientId(),
                            expiresAt,
                            now
                    );

                    return holdRepository.save(hold)
                            .flatMap(saved -> {
                                // 3. Check sold out after hold
                                return checkAndMarkSoldOut(command.scheduleId())
                                        .then(seatQuery.findAllBySchedule(UUID.fromString(coreProperties.eventId()), command.scheduleId())
                                                .filter(sv -> sv.getSeatId().equals(command.seatId()))
                                                .next()
                                                .map(sv -> new HoldResult(
                                                        saved.getId(),
                                                        command.scheduleId().toString(),
                                                        command.seatId(),
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
                seatQuery.countTotalSeats(UUID.fromString(coreProperties.eventId())),
                seatQuery.countHeldSeats(scheduleId),
                seatQuery.countConfirmedSeats(scheduleId)
        ).flatMap(tuple -> {
            if (availabilityService.isSoldOut(tuple.getT1(), tuple.getT2(), tuple.getT3())) {
                return soldOutPort.markSoldOut(coreProperties.eventId(),
                        scheduleId.toString(),
                        coreProperties.soldout().ttlSec());
            }
            return Mono.empty();
        });
    }
}
