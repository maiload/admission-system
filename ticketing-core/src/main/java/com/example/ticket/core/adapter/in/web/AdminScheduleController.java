package com.example.ticket.core.adapter.in.web;

import com.example.ticket.core.application.port.in.ActivateSchedulesInPort;
import com.example.ticket.core.application.port.in.ClearActiveSchedulesInPort;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.util.Map;

@RestController
@RequestMapping("/admin")
@RequiredArgsConstructor
public class AdminScheduleController {

    private final ActivateSchedulesInPort activateSchedulesInPort;
    private final ClearActiveSchedulesInPort clearActiveSchedulesInPort;

    @GetMapping("/schedules/activate")
    public Mono<Map<String, Object>> activateAll() {
        return activateSchedulesInPort.activateAll()
                .map(count -> Map.of(
                        "activated", count
                ));
    }

    @DeleteMapping("/schedules")
    public Mono<Map<String, Object>> clearAll() {
        return clearActiveSchedulesInPort.clearAll()
                .map(deleted -> Map.of(
                        "deleted", deleted
                ));
    }
}
