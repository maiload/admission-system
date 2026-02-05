package com.example.ticket.admission.application.dto;

public record IssueResult(long issued, long skipped, long remainingQueueSize) {

}
