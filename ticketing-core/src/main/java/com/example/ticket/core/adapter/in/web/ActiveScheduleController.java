package com.example.ticket.core.adapter.in.web;

import com.example.ticket.core.application.port.in.ActiveScheduleQueryInPort;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

@RestController
@RequestMapping("/core/schedules")
@RequiredArgsConstructor
public class ActiveScheduleController {

    private final ActiveScheduleQueryInPort activeScheduleQueryInPort;

    @GetMapping("/active")
    public Flux<ActiveScheduleQueryInPort.ActiveScheduleView> activeSchedules() {
        return activeScheduleQueryInPort.findActiveSchedules();
    }
}
