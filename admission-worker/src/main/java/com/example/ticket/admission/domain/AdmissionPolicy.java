package com.example.ticket.admission.domain;

/**
 * Pure domain logic: calculates how many admission tokens to issue per tick.
 * No framework dependencies.
 */
public final class AdmissionPolicy {

    private AdmissionPolicy() {
    }

    /**
     * Calculate the number of tokens to issue this tick.
     *
     * @param maxBatch       max issuable per single call (e.g., 200)
     * @param rateCap        per-second cap (e.g., 200)
     * @param concurrencyCap simultaneous active users cap (e.g., 10000)
     * @param currentRate    tokens already issued this second
     * @param currentActive  current active session count
     * @return number of tokens to issue (>= 0)
     */
    public static int calculateIssueCount(int maxBatch, int rateCap, int concurrencyCap,
                                           long currentRate, long currentActive) {
        long rateRoom = rateCap - currentRate;
        long concurrencyRoom = concurrencyCap - currentActive;

        if (rateRoom <= 0 || concurrencyRoom <= 0) {
            return 0;
        }

        return (int) Math.min(maxBatch, Math.min(rateRoom, concurrencyRoom));
    }
}
