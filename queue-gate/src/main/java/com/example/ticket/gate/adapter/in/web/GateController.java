package com.example.ticket.gate.adapter.in.web;

import com.example.ticket.common.port.IdGeneratorPort;
import com.example.ticket.gate.application.port.in.JoinQueueInPort;
import com.example.ticket.gate.application.port.in.StreamQueueInPort;
import com.example.ticket.gate.adapter.in.web.request.GateStreamRequest;
import com.example.ticket.gate.adapter.in.web.request.JoinRequest;
import com.example.ticket.gate.adapter.in.web.response.GateJoinResponse;
import com.example.ticket.gate.adapter.in.web.response.GateStatusResponse;
import com.example.ticket.gate.config.GateProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpCookie;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseCookie;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;

@RestController
@RequestMapping("/gate")
@RequiredArgsConstructor
public class GateController {

    private final JoinQueueInPort joinQueueInPort;
    private final StreamQueueInPort streamQueueInPort;
    private final IdGeneratorPort idGeneratorPort;
    private final GateProperties gateProperties;

    @PostMapping("/join")
    public Mono<GateJoinResponse> join(
            @RequestBody JoinRequest joinRequest,
            ServerHttpRequest request,
            ServerHttpResponse response) {

        return requireClientId(request, response)
                .map(clientId -> joinRequest.toCommand(clientId, isLoadTest(request)))
                .flatMap(joinQueueInPort::execute)
                .map(GateJoinResponse::fromResult);
    }

    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<GateStatusResponse>> stream(GateStreamRequest streamRequest) {
        return streamQueueInPort.stream(streamRequest.toQuery())
                .map(GateStatusResponse::fromResult)
                .map(response -> ServerSentEvent.<GateStatusResponse>builder()
                        .event("queue.progress")
                        .data(response)
                        .build());
    }

    @GetMapping("/status")
    public Mono<GateStatusResponse> status(GateStreamRequest streamRequest) {
        return streamQueueInPort.poll(streamRequest.toQuery())
                .map(GateStatusResponse::fromResult);
    }

    private Mono<String> requireClientId(ServerHttpRequest request, ServerHttpResponse response) {
        HttpCookie cookie = request.getCookies().getFirst(gateProperties.client().cookieName());
        if (cookie != null && !cookie.getValue().isBlank()) {
            return Mono.just(cookie.getValue());
        }

        String clientId = idGeneratorPort.generateUuid();
        ResponseCookie newCookie = ResponseCookie.from(gateProperties.client().cookieName(), clientId)
                .httpOnly(true)
                .path("/")
                .maxAge(Duration.ofDays(gateProperties.client().cookieMaxAgeDays()))
                .build();
        response.addCookie(newCookie);
        return Mono.just(clientId);
    }

    private boolean isLoadTest(ServerHttpRequest request) {
        String value = request.getHeaders().getFirst("X-Load-Test");
        return StringUtils.hasText(value) && value.equalsIgnoreCase("true");
    }
}
