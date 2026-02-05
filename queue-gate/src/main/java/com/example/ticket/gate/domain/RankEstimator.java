package com.example.ticket.gate.domain;

public final class RankEstimator {
    private static final int EASE_OUT_POW = 4;
    private static final long MAX_RANK = 1_000_000;
    private static final long RANK_0_5S = 1_000;
    private static final long RANK_1S = 10_000;
    private static final long RANK_10S = 100_000;

    private RankEstimator() {
    }

    public static long estimate(long deltaMs) {
        if (deltaMs < 0) {
            return 1;
        }

        double seconds = deltaMs / 1000.0;

        if (seconds <= 0.5) {
            double t = seconds / 0.5;
            return interpolateRank(1, RANK_0_5S, t);
        }

        if (seconds <= 1.0) {
            double t = (seconds - 0.5) / 0.5;
            return interpolateRank(RANK_0_5S, RANK_1S, t);
        }

        if (seconds <= 10.0) {
            return Math.min(RANK_10S, (long) Math.floor(seconds * RANK_1S));
        }

        return MAX_RANK;
    }

    private static long interpolateRank(long start, long end, double t) {
        double eased = easeOutPow4(clamp01(t));
        double value = start + (end - start) * eased;
        return (long) Math.floor(value);
    }

    private static double easeOutPow4(double t) {
        double inv = 1.0 - t;
        return 1.0 - Math.pow(inv, EASE_OUT_POW);
    }

    private static double clamp01(double t) {
        if (t < 0.0) {
            return 0.0;
        }
        return Math.min(t, 1.0);
    }
}
