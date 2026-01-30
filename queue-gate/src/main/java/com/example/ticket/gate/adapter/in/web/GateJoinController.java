package com.example.ticket.gate.adapter.in.web;

import com.example.ticket.common.error.BusinessException;
import com.example.ticket.common.error.ErrorCode;
import com.example.ticket.gate.application.dto.JoinResult;
import com.example.ticket.gate.application.usecase.JoinQueueUseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpCookie;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.Map;

@RestController
@RequestMapping("/gate")
@RequiredArgsConstructor
public class GateJoinController {

    private final JoinQueueUseCase joinQueueUseCase;

    @Value("${gate.client.cookie-name}")
    private String cookieName;

    @PostMapping("/join")
    public Mono<JoinResult> join(
            @RequestBody Map<String, String> body,
            ServerHttpRequest request) {

        String eventId = body.get("eventId");
        String scheduleId = body.get("scheduleId");
        String syncToken = body.get("syncToken");

        String clientId = resolveClientId(request);

        return joinQueueUseCase.execute(eventId, scheduleId, syncToken, clientId);
    }

    private String resolveClientId(ServerHttpRequest request) {
        HttpCookie cookie = request.getCookies().getFirst(cookieName);
        if (cookie == null || cookie.getValue().isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "Client ID cookie is missing");
        }
        return cookie.getValue();
    }
}
