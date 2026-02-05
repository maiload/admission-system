package com.example.ticket.core.application.dto.command;

import java.util.UUID;

public record CreateHoldCommand(
        String clientId,
        UUID scheduleId,
        UUID seatId
) {
}
