package com.example.ticket.core.domain;

import com.example.ticket.common.error.BusinessException;
import com.example.ticket.common.error.ErrorCode;

import java.time.Instant;
import java.util.UUID;

public record Hold(
        UUID id,
        UUID holdGroupId,
        UUID scheduleId,
        UUID seatId,
        String clientId,
        Instant expiresAt,
        Instant createdAt
) {
    public static Hold create(UUID id,
                              UUID holdGroupId,
                              UUID scheduleId,
                              UUID seatId,
                              String clientId,
                              Instant now,
                              int ttlSec) {
        Instant expiresAt = now.plusSeconds(ttlSec);
        return new Hold(id, holdGroupId, scheduleId, seatId, clientId, expiresAt, now);
    }

    public void validateConfirmable(String clientId, Instant now) {
        if (!belongsTo(clientId)) {
            throw new BusinessException(ErrorCode.HOLD_EXPIRED,
                    "Hold does not belong to client: " + clientId);
        }
        if (isExpired(now)) {
            throw new BusinessException(ErrorCode.HOLD_EXPIRED,
                    "Hold has expired at: " + expiresAt);
        }
    }

    public boolean isExpired(Instant now) {
        return now.isAfter(expiresAt);
    }

    public boolean belongsTo(String clientId) {
        return this.clientId.equals(clientId);
    }
}
