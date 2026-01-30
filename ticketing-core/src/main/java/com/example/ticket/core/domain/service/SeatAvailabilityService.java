package com.example.ticket.core.domain.service;

/**
 * Domain service for seat availability logic.
 * Pure domain — no framework dependency.
 */
public class SeatAvailabilityService {

    public boolean isSoldOut(long totalSeats, long heldCount, long confirmedCount) {
        return (heldCount + confirmedCount) >= totalSeats;
    }
}
