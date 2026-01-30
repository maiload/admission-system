package com.example.ticket.admission.adapter.in.scheduler;

import com.example.ticket.admission.application.engine.AdmissionEngine;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class AdmissionPoller {

    private final AdmissionEngine admissionEngine;

    @Scheduled(fixedDelayString = "${admission.worker.poll-interval-ms}")
    public void poll() {
        admissionEngine.tick()
                .subscribe(
                        null,
                        e -> log.error("Admission tick failed", e)
                );
    }
}
