package com.example.ticket.core.adapter.in.web;

import com.example.ticket.core.application.command.EnterCoreUseCase;
import com.example.ticket.core.application.dto.command.EnterResult;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.Map;

@RestController
@RequestMapping("/core")
@RequiredArgsConstructor
public class CoreEnterController {

    private final EnterCoreUseCase enterCoreUseCase;

    @PostMapping("/enter")
    public Mono<EnterResult> enter(
            @RequestHeader("Authorization") String authorization,
            @RequestBody Map<String, String> body) {

        String enterToken = authorization.replace("Bearer ", "");
        String eventId = body.get("eventId");
        String scheduleId = body.get("scheduleId");

        return enterCoreUseCase.execute(enterToken, eventId, scheduleId);
    }
}
