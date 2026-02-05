package com.example.ticket.core.application.port.out;

import com.example.ticket.core.application.dto.schedule.ScheduleStartView;
import reactor.core.publisher.Flux;

public interface ScheduleReadPort {

    Flux<ScheduleStartView> findAll();
}
