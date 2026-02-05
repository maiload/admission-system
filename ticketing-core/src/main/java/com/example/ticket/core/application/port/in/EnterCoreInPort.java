package com.example.ticket.core.application.port.in;

import com.example.ticket.core.application.dto.command.EnterCommand;
import com.example.ticket.core.application.dto.command.EnterResult;
import reactor.core.publisher.Mono;

public interface EnterCoreInPort {

    Mono<EnterResult> execute(EnterCommand command);
}
