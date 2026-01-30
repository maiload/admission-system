package com.example.ticket.gate.domain;

/**
 * Non-linear rank bucket for delta-based rank estimation.
 * Maps a click-timing delta (ms after startAt) to an estimated queue rank.
 */
public record RankBucket(long minDeltaMs, long maxDeltaMs, long estimatedRank) {

    public boolean contains(long deltaMs) {
        return deltaMs >= minDeltaMs && deltaMs < maxDeltaMs;
    }
}
