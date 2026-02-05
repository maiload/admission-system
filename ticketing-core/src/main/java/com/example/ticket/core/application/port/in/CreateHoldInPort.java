package com.example.ticket.core.application.port.in;

import com.example.ticket.core.application.dto.command.CreateHoldCommand;
import com.example.ticket.core.application.dto.command.HoldResult;
import reactor.core.publisher.Mono;

public interface CreateHoldInPort {

    Mono<HoldResult> execute(CreateHoldCommand command);
}
