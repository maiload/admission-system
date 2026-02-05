package com.example.ticket.core.application.port.in;

import com.example.ticket.core.application.dto.command.ConfirmHoldCommand;
import com.example.ticket.core.application.dto.command.ConfirmResult;
import reactor.core.publisher.Mono;

public interface ConfirmHoldInPort {

    Mono<ConfirmResult> execute(ConfirmHoldCommand command);
}
