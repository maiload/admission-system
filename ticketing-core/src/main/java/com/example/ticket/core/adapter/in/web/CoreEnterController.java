package com.example.ticket.core.adapter.in.web;

import com.example.ticket.core.application.port.in.EnterCoreInPort;
import com.example.ticket.core.adapter.in.web.request.CoreEnterRequest;
import com.example.ticket.core.adapter.in.web.response.CoreEnterResponse;
import com.example.ticket.core.config.CoreProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseCookie;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.time.Duration;

@RestController
@RequestMapping("/core")
@RequiredArgsConstructor
public class CoreEnterController {

    private final EnterCoreInPort enterCoreInPort;
    private final CoreProperties coreProperties;

    @PostMapping("/enter")
    public Mono<CoreEnterResponse> enter(
            @RequestHeader("Authorization") String authorization,
            @RequestBody CoreEnterRequest request,
            ServerHttpResponse response) {

        String enterToken = authorization.replace("Bearer ", "");

        return enterCoreInPort.execute(request.toCommand(enterToken))
                .map(result -> {
                    ResponseCookie cookie = ResponseCookie.from(
                                    coreProperties.session().cookieName(),
                                    result.coreSessionToken())
                            .httpOnly(true)
                            .path("/")
                            .maxAge(Duration.ofSeconds(result.expiresInSec()))
                            .build();
                    response.addCookie(cookie);
                    return CoreEnterResponse.fromResult(result);
                });
    }
}
