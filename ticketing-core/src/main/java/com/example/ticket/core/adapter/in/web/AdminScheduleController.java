package com.example.ticket.core.adapter.in.web;

import com.example.ticket.core.application.port.in.ActiveScheduleInPort;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.util.Map;

@RestController
@RequestMapping("/core/admin")
@RequiredArgsConstructor
public class AdminScheduleController {

    private final ActiveScheduleInPort activeScheduleInPort;

    @GetMapping("/schedules/activate")
    public Mono<Map<String, Object>> activateAll() {
        return activeScheduleInPort.activateAll()
                .map(count -> Map.of("activated", count));
    }

    @DeleteMapping("/schedules")
    public Mono<Map<String, Object>> clearAll() {
        return activeScheduleInPort.clearAll()
                .map(deleted -> Map.of("deleted", deleted));
    }
}
