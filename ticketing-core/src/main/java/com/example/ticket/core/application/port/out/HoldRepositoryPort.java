package com.example.ticket.core.application.port.out;

import com.example.ticket.core.domain.hold.Hold;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.UUID;

public interface HoldRepositoryPort {

    Mono<Hold> save(Hold hold);

    Mono<Hold> findById(UUID holdId);

    Mono<Hold> findByScheduleAndClient(UUID scheduleId, String clientId);

    Mono<Long> countByScheduleAndClient(UUID scheduleId, String clientId);

    Mono<Void> deleteById(UUID holdId);

    Mono<Long> deleteExpired(Instant now);
}
