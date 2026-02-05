package com.example.ticket.core.application.port.in;

import reactor.core.publisher.Mono;

public interface ActivateSchedulesInPort {

    Mono<Long> activateAll();
}
