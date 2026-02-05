package com.example.ticket.admission.adapter.in.scheduler;

import com.example.ticket.admission.application.job.AdmissionJob;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class AdmissionPoller {

    private final AdmissionJob admissionJob;

    @Scheduled(fixedDelayString = "${admission.worker.poll-interval-ms}")
    public void poll() {
        admissionJob.tick()
                .doOnError(e -> log.error("Admission tick failed", e))
                .subscribe();
    }
}
