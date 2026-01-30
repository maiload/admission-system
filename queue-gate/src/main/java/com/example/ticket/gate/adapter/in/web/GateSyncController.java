package com.example.ticket.gate.adapter.in.web;

import com.example.ticket.gate.application.dto.SyncResult;
import com.example.ticket.gate.application.usecase.SyncUseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpCookie;
import org.springframework.http.ResponseCookie;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.UUID;

@RestController
@RequestMapping("/gate")
@RequiredArgsConstructor
public class GateSyncController {

    private final SyncUseCase syncUseCase;

    @Value("${gate.client.cookie-name}")
    private String cookieName;

    @Value("${gate.client.cookie-max-age-days}")
    private int cookieMaxAgeDays;

    @GetMapping("/sync")
    public Mono<SyncResult> sync(
            @RequestParam String eventId,
            @RequestParam String scheduleId,
            ServerHttpRequest request,
            ServerHttpResponse response) {

        // Issue clientId cookie if missing
        String clientId = resolveClientId(request);
        ResponseCookie cookie = ResponseCookie.from(cookieName, clientId)
                .httpOnly(true)
                .path("/")
                .maxAge(Duration.ofDays(cookieMaxAgeDays))
                .build();
        response.addCookie(cookie);

        return syncUseCase.execute(eventId, scheduleId);
    }

    private String resolveClientId(ServerHttpRequest request) {
        HttpCookie existing = request.getCookies().getFirst(cookieName);
        if (existing != null && !existing.getValue().isBlank()) {
            return existing.getValue();
        }
        return UUID.randomUUID().toString();
    }
}
