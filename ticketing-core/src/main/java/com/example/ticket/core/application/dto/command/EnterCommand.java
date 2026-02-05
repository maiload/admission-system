package com.example.ticket.core.application.dto.command;

public record EnterCommand(
        String enterToken,
        String eventId,
        String scheduleId
) {
}
