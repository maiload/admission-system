package com.example.ticket.admission.application.dto;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public class IssueResult {

    private final long issued;
    private final long skipped;
    private final long remainingQueueSize;
}
