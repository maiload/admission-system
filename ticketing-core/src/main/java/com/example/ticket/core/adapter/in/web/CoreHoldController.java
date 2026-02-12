package com.example.ticket.core.adapter.in.web;

import com.example.ticket.common.error.BusinessException;
import com.example.ticket.common.error.ErrorCode;
import com.example.ticket.common.util.TokenFormat;
import com.example.ticket.core.application.port.in.HoldInPort;
import com.example.ticket.core.adapter.in.web.request.HoldCreateRequest;
import com.example.ticket.core.adapter.in.web.response.HoldConfirmResponse;
import com.example.ticket.core.adapter.in.web.response.HoldCreateResponse;
import com.example.ticket.core.application.port.in.HoldInPort.ConfirmHoldCommand;
import com.example.ticket.core.application.port.out.TokenSignerPort;
import com.example.ticket.core.config.CoreProperties;
import com.example.ticket.core.application.port.out.SessionPort;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpCookie;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.UUID;

@RestController
@RequestMapping("/core")
@RequiredArgsConstructor
public class CoreHoldController {

    private final HoldInPort holdInPort;
    private final SessionPort sessionPort;
    private final CoreProperties coreProperties;
    private final TokenSignerPort tokenSignerPort;

    @PostMapping("/holds")
    @ResponseStatus(HttpStatus.CREATED)
    public Mono<HoldCreateResponse> createHold(
            @RequestBody HoldCreateRequest request,
            ServerHttpRequest httpRequest) {

        String token = resolveSessionToken(httpRequest);
        String[] sessionInfo = extractSessionInfo(token);
        String sessionId = sessionInfo[0];
        String eventId = request.eventId();
        String scheduleId = request.scheduleId();
        return sessionPort.validateSession(new SessionPort.ValidateQuery(eventId, scheduleId, sessionId))
                .switchIfEmpty(Mono.error(new BusinessException(ErrorCode.SESSION_TOKEN_INVALID)))
                .flatMap(clientId ->
                        sessionPort.refreshSession(new SessionPort.RefreshCommand(eventId, scheduleId, sessionId))
                                .then(holdInPort.createHold(request.toCommand(clientId)))
                                .map(HoldCreateResponse::fromResult)
                );
    }

    @PostMapping("/holds/{holdGroupId}/confirm")
    public Mono<HoldConfirmResponse> confirmHold(
            @PathVariable UUID holdGroupId,
            ServerHttpRequest httpRequest) {

        String token = resolveSessionToken(httpRequest);
        String[] sessionInfo = extractSessionInfo(token);
        String sessionId = sessionInfo[0];
        String eventId = sessionInfo[1];
        String scheduleId = sessionInfo[2];

        return sessionPort.validateSession(new SessionPort.ValidateQuery(eventId, scheduleId, sessionId))
                .switchIfEmpty(Mono.error(new BusinessException(ErrorCode.SESSION_TOKEN_INVALID)))
                .flatMap(clientId ->
                        holdInPort.confirmHold(new ConfirmHoldCommand(
                                clientId,
                                holdGroupId,
                                UUID.fromString(eventId),
                                UUID.fromString(scheduleId),
                                sessionId
                        ))
                                .map(HoldConfirmResponse::fromResult)
                );
    }

    private String[] extractSessionInfo(String token) {
        String payload = tokenSignerPort.verifySessionToken(token);
        if (payload == null) {
            throw new BusinessException(ErrorCode.SESSION_TOKEN_INVALID);
        }
        return TokenFormat.splitClaims(payload);
        // [0]=sessionId, [1]=eventId, [2]=scheduleId, [3]=expMs
    }

    private String resolveSessionToken(ServerHttpRequest request) {
        HttpCookie cookie = request.getCookies().getFirst(coreProperties.session().cookieName());
        if (cookie == null || cookie.getValue().isBlank()) {
            throw new BusinessException(ErrorCode.SESSION_TOKEN_REQUIRED);
        }
        return cookie.getValue();
    }

}
