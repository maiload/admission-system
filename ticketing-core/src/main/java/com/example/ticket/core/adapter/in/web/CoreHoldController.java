package com.example.ticket.core.adapter.in.web;

import com.example.ticket.common.error.BusinessException;
import com.example.ticket.common.error.ErrorCode;
import com.example.ticket.common.token.HmacSigner;
import com.example.ticket.common.util.TokenFormat;
import com.example.ticket.core.application.command.ConfirmHoldUseCase;
import com.example.ticket.core.application.command.CreateHoldUseCase;
import com.example.ticket.core.application.dto.command.ConfirmResult;
import com.example.ticket.core.application.dto.command.HoldResult;
import com.example.ticket.core.application.port.out.SessionPort;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/core")
@RequiredArgsConstructor
public class CoreHoldController {

    private final CreateHoldUseCase createHoldUseCase;
    private final ConfirmHoldUseCase confirmHoldUseCase;
    private final SessionPort sessionPort;

    @Value("${core.session.secret}")
    private String sessionSecret;

    @PostMapping("/holds")
    @ResponseStatus(HttpStatus.CREATED)
    public Mono<HoldResult> createHold(
            @RequestHeader("Authorization") String authorization,
            @RequestBody Map<String, String> body) {

        String[] sessionInfo = extractSessionInfo(authorization);
        String sessionId = sessionInfo[0];
        String eventId = body.get("eventId");
        String scheduleId = body.get("scheduleId");
        UUID seatId = UUID.fromString(body.get("seatId"));

        return sessionPort.validateSession(eventId, scheduleId, sessionId)
                .switchIfEmpty(Mono.error(new BusinessException(ErrorCode.SESSION_TOKEN_INVALID)))
                .flatMap(clientId ->
                        sessionPort.refreshSession(eventId, scheduleId, sessionId)
                                .then(createHoldUseCase.execute(clientId, UUID.fromString(scheduleId), seatId))
                );
    }

    @PostMapping("/holds/{holdId}/confirm")
    public Mono<ConfirmResult> confirmHold(
            @RequestHeader("Authorization") String authorization,
            @PathVariable UUID holdId) {

        String[] sessionInfo = extractSessionInfo(authorization);
        String sessionId = sessionInfo[0];
        String eventId = sessionInfo[1];
        String scheduleId = sessionInfo[2];

        return sessionPort.validateSession(eventId, scheduleId, sessionId)
                .switchIfEmpty(Mono.error(new BusinessException(ErrorCode.SESSION_TOKEN_INVALID)))
                .flatMap(clientId ->
                        confirmHoldUseCase.execute(clientId, holdId, scheduleId, sessionId)
                );
    }

    private String[] extractSessionInfo(String authorization) {
        String token = authorization.replace("Bearer ", "");
        String payload = HmacSigner.verifyAndExtract(token, sessionSecret);
        if (payload == null) {
            throw new BusinessException(ErrorCode.SESSION_TOKEN_INVALID);
        }
        return TokenFormat.splitClaims(payload);
        // [0]=sessionId, [1]=eventId, [2]=scheduleId, [3]=expMs
    }
}
