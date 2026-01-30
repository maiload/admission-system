package com.example.ticket.gate.application.dto;

import com.example.ticket.gate.domain.QueueStatus;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public class QueueProgressDto {

    private final QueueStatus status;
    private final long estimatedRank;
    private final long totalInQueue;
    private final String enterToken;   // non-null when ADMISSION_GRANTED
    private final String eventId;
    private final String scheduleId;
    private final long serverTimeMs;
}
