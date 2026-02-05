package com.example.ticket.core.application.dto.schedule;

import java.time.Instant;
import java.util.UUID;

public record ScheduleStartView(UUID scheduleId, UUID eventId, Instant startAt) {
}
