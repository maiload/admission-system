package com.example.ticket.gate.adapter.in.web;

import com.example.ticket.gate.application.dto.QueueProgressDto;
import com.example.ticket.gate.application.usecase.StreamQueueUseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/gate")
@RequiredArgsConstructor
public class GateStatusController {

    private final StreamQueueUseCase streamQueueUseCase;

    @GetMapping("/status")
    public Mono<QueueProgressDto> status(
            @RequestParam String queueToken,
            @RequestParam String eventId,
            @RequestParam String scheduleId) {

        return streamQueueUseCase.poll(eventId, scheduleId, queueToken);
    }
}
