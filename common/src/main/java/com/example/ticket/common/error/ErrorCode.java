package com.example.ticket.common.error;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum ErrorCode {

    // 400
    INVALID_SYNC_TOKEN(400, "Invalid or expired sync token"),
    TOO_EARLY(400, "Event has not started yet"),
    INVALID_WINDOW(400, "Join window has expired"),
    INVALID_REQUEST(400, "Invalid request parameters"),
    CLIENT_ID_REQUIRED(400, "Client id cookie is required"),

    // 401
    ENTER_TOKEN_REQUIRED(401, "Enter token is required"),
    SESSION_TOKEN_REQUIRED(401, "Core session token is required"),

    // 403
    ENTER_TOKEN_INVALID(403, "Enter token is invalid or expired"),
    SESSION_TOKEN_INVALID(403, "Core session token is invalid or expired"),

    // 404
    NOT_FOUND(404, "Resource not found"),
    SCHEDULE_NOT_FOUND(404, "Schedule not found"),

    // 409
    ALREADY_JOINED(409, "Already joined the queue"),
    SEAT_ALREADY_HELD(409, "Seat is already held by another user"),
    HOLD_EXPIRED(409, "Hold has expired"),
    ALREADY_HOLDING(409, "Already holding another seat"),
    HOLD_LIMIT_EXCEEDED(409, "Seat hold limit exceeded"),
    SOLD_OUT(409, "All seats are sold out"),

    // 429
    TOO_MANY_REQUESTS(429, "Too many requests"),

    // 503
    EVENT_NOT_OPEN(503, "Event is not open yet");

    private final int httpStatus;
    private final String message;
}
