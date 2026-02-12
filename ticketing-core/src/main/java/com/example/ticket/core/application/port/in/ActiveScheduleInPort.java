package com.example.ticket.core.application.port.in;

import reactor.core.publisher.Mono;

public interface ActiveScheduleInPort {

    Mono<Long> activateAll();

    Mono<Long> clearAll();
}
