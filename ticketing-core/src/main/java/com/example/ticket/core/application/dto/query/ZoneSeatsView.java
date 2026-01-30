package com.example.ticket.core.application.dto.query;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.List;

@Getter
@RequiredArgsConstructor
public class ZoneSeatsView {

    private final String zone;
    private final List<SeatView> seats;
}
