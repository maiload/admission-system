package com.example.ticket.core.domain.seat;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.UUID;

@Getter
@RequiredArgsConstructor
public class Seat {

    private final UUID id;
    private final UUID eventId;
    private final String zone;
    private final int seatNo;
}
