package com.example.ticket.gate.application.dto;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public class SyncResult {

    private final long serverTimeMs;
    private final long startAtMs;
    private final String syncToken;
    private final int windowMs;
}
