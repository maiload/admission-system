package com.example.ticket.core.adapter.in.web;

import com.example.ticket.common.error.BusinessException;
import com.example.ticket.common.error.ErrorCode;
import com.example.ticket.common.token.HmacSigner;
import com.example.ticket.common.util.TokenFormat;
import com.example.ticket.core.application.port.in.ConfirmHoldInPort;
import com.example.ticket.core.application.port.in.CreateHoldInPort;
import com.example.ticket.core.adapter.in.web.request.CoreHoldRequest;
import com.example.ticket.core.adapter.in.web.response.CoreConfirmResponse;
import com.example.ticket.core.adapter.in.web.response.CoreHoldResponse;
import com.example.ticket.core.application.dto.command.ConfirmHoldCommand;
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

    private final CreateHoldInPort createHoldInPort;
    private final ConfirmHoldInPort confirmHoldInPort;
    private final SessionPort sessionPort;
    private final CoreProperties coreProperties;

    @PostMapping("/holds")
    @ResponseStatus(HttpStatus.CREATED)
    public Mono<CoreHoldResponse> createHold(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestBody CoreHoldRequest request,
            ServerHttpRequest httpRequest) {

        String token = resolveSessionToken(authorization, httpRequest);
        String[] sessionInfo = extractSessionInfo(token);
        String sessionId = sessionInfo[0];
        String eventId = request.eventId();
        String scheduleId = request.scheduleId();
        return sessionPort.validateSession(eventId, scheduleId, sessionId)
                .switchIfEmpty(Mono.error(new BusinessException(ErrorCode.SESSION_TOKEN_INVALID)))
                .flatMap(clientId ->
                        sessionPort.refreshSession(eventId, scheduleId, sessionId)
                                .then(createHoldInPort.execute(request.toCommand(clientId)))
                                .map(CoreHoldResponse::fromResult)
                );
    }

    @PostMapping("/holds/{holdId}/confirm")
    public Mono<CoreConfirmResponse> confirmHold(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable UUID holdId,
            ServerHttpRequest httpRequest) {

        String token = resolveSessionToken(authorization, httpRequest);
        String[] sessionInfo = extractSessionInfo(token);
        String sessionId = sessionInfo[0];
        String eventId = sessionInfo[1];
        String scheduleId = sessionInfo[2];

        return sessionPort.validateSession(eventId, scheduleId, sessionId)
                .switchIfEmpty(Mono.error(new BusinessException(ErrorCode.SESSION_TOKEN_INVALID)))
                .flatMap(clientId ->
                        confirmHoldInPort.execute(new ConfirmHoldCommand(
                                clientId, holdId, scheduleId, sessionId))
                                .map(CoreConfirmResponse::fromResult)
                );
    }

    private String[] extractSessionInfo(String token) {
        String payload = HmacSigner.verifyAndExtract(token, coreProperties.session().secret());
        if (payload == null) {
            throw new BusinessException(ErrorCode.SESSION_TOKEN_INVALID);
        }
        return TokenFormat.splitClaims(payload);
        // [0]=sessionId, [1]=eventId, [2]=scheduleId, [3]=expMs
    }

    private String resolveSessionToken(String authorization, ServerHttpRequest request) {
        if (authorization != null && authorization.startsWith("Bearer ")) {
            return authorization.replace("Bearer ", "");
        }
        HttpCookie cookie = request.getCookies().getFirst(coreProperties.session().cookieName());
        if (cookie == null || cookie.getValue().isBlank()) {
            throw new BusinessException(ErrorCode.SESSION_TOKEN_REQUIRED);
        }
        return cookie.getValue();
    }
}
