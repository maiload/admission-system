package com.example.ticket.gate.adapter.in.web;

import com.example.ticket.gate.application.dto.QueueProgressDto;
import com.example.ticket.gate.application.usecase.StreamQueueUseCase;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

@RestController
@RequestMapping("/gate")
@RequiredArgsConstructor
public class GateStreamController {

    private final StreamQueueUseCase streamQueueUseCase;
    private final JsonMapper jsonMapper;

    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>> stream(
            @RequestParam String queueToken,
            @RequestParam String eventId,
            @RequestParam String scheduleId) {

        return streamQueueUseCase.stream(eventId, scheduleId, queueToken)
                .map(dto -> ServerSentEvent.<String>builder()
                        .event("queue.progress")
                        .data(toJson(dto))
                        .build());
    }

    private String toJson(QueueProgressDto dto) {
        try {
            return jsonMapper.writeValueAsString(dto);
        } catch (JacksonException e) {
            return "{}";
        }
    }
}
