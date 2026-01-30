package com.example.ticket.gate.application.dto;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public class JoinResult {

    private final String queueToken;
    private final long estimatedRank;
    private final String sseUrl;
    private final boolean alreadyJoined;
}
