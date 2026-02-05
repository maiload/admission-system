package com.example.ticket.core.application.dto.command;

public record EnterResult(String coreSessionToken, int expiresInSec, String eventId, String scheduleId) {

}
