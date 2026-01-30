package com.example.ticket.core.domain.hold;

import com.example.ticket.common.error.BusinessException;
import com.example.ticket.common.error.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Getter
@RequiredArgsConstructor
public class Hold {

    private final UUID id;
    private final UUID scheduleId;
    private final UUID seatId;
    private final String clientId;
    private final Instant expiresAt;
    private final Instant createdAt;

    public boolean isExpired(Instant now) {
        return now.isAfter(expiresAt);
    }

    public boolean belongsTo(String clientId) {
        return this.clientId.equals(clientId);
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
}
