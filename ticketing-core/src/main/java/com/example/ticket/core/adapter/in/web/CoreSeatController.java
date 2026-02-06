package com.example.ticket.core.adapter.in.web;

import com.example.ticket.common.error.BusinessException;
import com.example.ticket.common.error.ErrorCode;
import com.example.ticket.common.util.TokenFormat;
import com.example.ticket.core.application.port.in.SeatQueryInPort;
import com.example.ticket.core.adapter.in.web.request.CoreSeatRequest;
import com.example.ticket.core.adapter.in.web.response.CoreSeatResponse;
import com.example.ticket.core.application.port.out.TokenSignerPort;
import com.example.ticket.core.config.CoreProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpCookie;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;


@RestController
@RequestMapping("/core")
@RequiredArgsConstructor
public class CoreSeatController {

    private final SeatQueryInPort seatQueryInPort;
    private final CoreProperties coreProperties;
    private final TokenSignerPort tokenSignerPort;

    @GetMapping("/seats")
    public Mono<CoreSeatResponse> getSeats(
            CoreSeatRequest request,
            ServerHttpRequest httpRequest) {

        String token = resolveSessionToken(httpRequest);
        String sessionId = extractSessionId(token);

        return seatQueryInPort.execute(request.toQuery(sessionId))
                .map(zones -> CoreSeatResponse.fromResult(
                        request.eventId(),
                        request.scheduleId(),
                        zones
                ));
    }

    private String extractSessionId(String token) {
        String payload = tokenSignerPort.verifySessionToken(token);
        if (payload == null) {
            throw new BusinessException(ErrorCode.SESSION_TOKEN_INVALID);
        }
        String[] claims = TokenFormat.splitClaims(payload);
        return claims[0]; // sessionId
    }

    private String resolveSessionToken(ServerHttpRequest request) {
        HttpCookie cookie = request.getCookies().getFirst(coreProperties.session().cookieName());
        if (cookie == null || cookie.getValue().isBlank()) {
            throw new BusinessException(ErrorCode.SESSION_TOKEN_REQUIRED);
        }
        return cookie.getValue();
    }
}
