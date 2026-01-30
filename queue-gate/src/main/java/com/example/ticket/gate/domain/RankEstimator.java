package com.example.ticket.gate.domain;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Estimates queue rank from click-timing delta.
 * Uses non-linear bucketing: earlier clicks get better estimated ranks.
 * Pure domain logic — no framework dependencies.
 */
public final class RankEstimator {

    private static final List<RankBucket> BUCKETS = List.of(
            new RankBucket(0, 20, 1_000),
            new RankBucket(20, 50, 4_000),
            new RankBucket(50, 100, 5_000),
            new RankBucket(100, 200, 20_000),
            new RankBucket(200, 500, 70_000),
            new RankBucket(500, 2_000, 200_000),
            new RankBucket(2_000, 10_000, 700_000)
    );

    private static final long MAX_RANK = 1_000_000;

    private RankEstimator() {
    }

    /**
     * Estimate rank from delta (receivedAtMs - startAtMs).
     *
     * @param deltaMs milliseconds after event start
     * @return estimated rank (1-based)
     */
    public static long estimate(long deltaMs) {
        if (deltaMs < 0) {
            return 1;
        }

        for (RankBucket bucket : BUCKETS) {
            if (bucket.contains(deltaMs)) {
                return bucket.estimatedRank();
            }
        }

        return MAX_RANK;
    }

    /**
     * Compute ZSET score: rank * 10 + tieBreaker (0~9).
     * Tie-breaker adds randomness within same rank bucket.
     */
    public static double computeScore(long estimatedRank) {
        int tieBreaker = ThreadLocalRandom.current().nextInt(10);
        return estimatedRank * 10.0 + tieBreaker;
    }
}
