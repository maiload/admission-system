package com.example.ticket.admission.domain;

public record AdmissionConfig(
        int maxBatch,
        int rateCap,
        int concurrencyCap,
        int enterTtlSec,
        int qstateTtlSec,
        int rateTtlSec
) {}
