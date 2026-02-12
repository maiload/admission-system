package com.example.ticket.admission.application.port.out;

import reactor.core.publisher.Flux;

public interface ActiveSchedulePort {

    Flux<String> getActiveSchedules();
}
