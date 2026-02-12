package com.example.ticket.admission.application.port.out;

import com.example.ticket.admission.domain.AdmissionConfig;
import reactor.core.publisher.Mono;

import java.util.List;

public interface IssuerPort {

    Mono<IssueResult> issue(String eventId, String scheduleId,
                            AdmissionConfig config,
                            List<String> tokenPairs);

    record IssueResult(long issued, long skipped, long remainingQueueSize) {
    }
}
