package com.example.ticket.core.adapter.in.web;

import com.example.ticket.common.error.BusinessException;
import com.example.ticket.common.error.ErrorCode;
import com.example.ticket.common.token.HmacSigner;
import com.example.ticket.common.util.TokenFormat;
import com.example.ticket.core.application.dto.query.ZoneSeatsView;
import com.example.ticket.core.application.query.SeatQueryUseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/core")
@RequiredArgsConstructor
public class CoreSeatController {

    private final SeatQueryUseCase seatQueryUseCase;

    @Value("${core.session.secret}")
    private String sessionSecret;

    @GetMapping("/seats")
    public Mono<Map<String, Object>> getSeats(
            @RequestHeader("Authorization") String authorization,
            @RequestParam String eventId,
            @RequestParam String scheduleId) {

        String sessionId = extractSessionId(authorization);

        return seatQueryUseCase.execute(sessionId, eventId, UUID.fromString(scheduleId))
                .map(zones -> Map.of(
                        "eventId", eventId,
                        "scheduleId", scheduleId,
                        "zones", (Object) zones,
                        "serverTimeMs", System.currentTimeMillis()
                ));
    }

    private String extractSessionId(String authorization) {
        String token = authorization.replace("Bearer ", "");
        String payload = HmacSigner.verifyAndExtract(token, sessionSecret);
        if (payload == null) {
            throw new BusinessException(ErrorCode.SESSION_TOKEN_INVALID);
        }
        String[] claims = TokenFormat.splitClaims(payload);
        return claims[0]; // sessionId
    }
}
