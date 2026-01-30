package com.example.ticket.core.application.dto.command;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public class EnterResult {

    private final String coreSessionToken;
    private final int expiresInSec;
    private final String eventId;
    private final String scheduleId;
}
