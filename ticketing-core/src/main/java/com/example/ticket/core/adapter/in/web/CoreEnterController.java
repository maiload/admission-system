package com.example.ticket.core.adapter.in.web;

import com.example.ticket.common.error.BusinessException;
import com.example.ticket.common.error.ErrorCode;
import com.example.ticket.common.util.TokenFormat;
import com.example.ticket.core.application.port.in.EnterCoreInPort;
import com.example.ticket.core.adapter.in.web.request.CoreEnterRequest;
import com.example.ticket.core.adapter.in.web.response.CoreEnterResponse;
import com.example.ticket.core.application.port.out.SessionPort;
import com.example.ticket.core.application.port.out.TokenSignerPort;
import com.example.ticket.core.config.CoreProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpCookie;
import org.springframework.http.ResponseCookie;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.time.Duration;

@RestController
@RequestMapping("/core")
@RequiredArgsConstructor
public class CoreEnterController {

    private final EnterCoreInPort enterCoreInPort;
    private final SessionPort sessionPort;
    private final TokenSignerPort tokenSignerPort;
    private final CoreProperties coreProperties;

    @PostMapping("/enter")
    public Mono<CoreEnterResponse> enter(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestBody CoreEnterRequest request,
            ServerHttpRequest httpRequest,
            ServerHttpResponse response) {

        return tryExistingSession(httpRequest, request)
                .switchIfEmpty(Mono.defer(() -> {
                    String enterToken = extractEnterToken(authorization);
                    return enterCoreInPort.execute(request.toCommand(enterToken))
                            .map(result -> {
                                ResponseCookie cookie = ResponseCookie.from(
                                                coreProperties.session().cookieName(),
                                                result.sessionToken())
                                        .httpOnly(true)
                                        .path("/")
                                        .maxAge(Duration.ofSeconds(result.sessionTtlSec()))
                                        .build();
                                response.addCookie(cookie);
                                return CoreEnterResponse.fromResult(result);
                            });
                }));
    }

    private Mono<CoreEnterResponse> tryExistingSession(
            ServerHttpRequest httpRequest, CoreEnterRequest request) {
        HttpCookie cookie = httpRequest.getCookies()
                .getFirst(coreProperties.session().cookieName());
        if (cookie == null || cookie.getValue().isBlank()) {
            return Mono.empty();
        }

        String payload = tokenSignerPort.verifySessionToken(cookie.getValue());
        if (payload == null) {
            return Mono.empty();
        }

        String[] claims = TokenFormat.splitClaims(payload);
        // claims: [sessionId, eventId, scheduleId, expMs]
        if (claims.length < 4) {
            return Mono.empty();
        }

        String sessionId = claims[0];
        String eventId = claims[1];
        String scheduleId = claims[2];

        if (!eventId.equals(request.eventId()) || !scheduleId.equals(request.scheduleId())) {
            return Mono.empty();
        }

        return sessionPort.validateSession(
                        new SessionPort.ValidateQuery(eventId, scheduleId, sessionId))
                .flatMap(clientId ->
                        sessionPort.refreshSession(
                                        new SessionPort.RefreshCommand(eventId, scheduleId, sessionId))
                                .thenReturn(new CoreEnterResponse(
                                        coreProperties.session().ttlSec(),
                                        eventId,
                                        scheduleId))
                );
    }

    private String extractEnterToken(String authorization) {
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            throw new BusinessException(ErrorCode.ENTER_TOKEN_REQUIRED);
        }
        return authorization.replace("Bearer ", "");
    }
}
