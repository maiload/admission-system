package com.example.ticket.core.application.dto.command;

import java.util.UUID;

public record ConfirmHoldCommand(
        String clientId,
        UUID holdId,
        String scheduleId,
        String sessionId
) {
}
