package com.example.ticket.admission.application.port.out;

import com.example.ticket.admission.application.dto.IssueResult;
import com.example.ticket.admission.domain.AdmissionConfig;
import reactor.core.publisher.Mono;

import java.util.List;

public interface IssuerPort {

    Mono<IssueResult> issue(String eventId, String scheduleId,
                            AdmissionConfig config,
                            List<String> tokenPairs);

    // 시뮬레이션 비활성 시 항상 true, 활성 시 큐 선두의 대기 시간 경과 여부 반환
    Mono<Boolean> isHeadReady(String eventId, String scheduleId, AdmissionConfig config);
}
