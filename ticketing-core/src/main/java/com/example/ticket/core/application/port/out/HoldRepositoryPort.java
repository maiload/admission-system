package com.example.ticket.core.application.port.out;

import com.example.ticket.core.domain.Hold;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.UUID;

public interface HoldRepositoryPort {

    Mono<Hold> save(Hold hold);

    Mono<Hold> findById(UUID holdId);

    Flux<Hold> findByGroupId(UUID holdGroupId);

    Mono<Hold> findByScheduleAndClient(UUID scheduleId, String clientId);


    Mono<Void> deleteById(UUID holdId);

    Mono<Void> deleteByGroupId(UUID holdGroupId);

    Mono<Long> deleteExpired(Instant now);
}
