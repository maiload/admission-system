package com.example.ticket.core.application.port.out;

import reactor.core.publisher.Flux;

public interface ActiveScheduleReadPort {

    Flux<ActiveSchedule> findAll();

    record ActiveSchedule(String eventId, String scheduleId) {}
}
