package com.example.ticket.core.application.port.in;

import com.example.ticket.core.application.dto.query.SeatQuery;
import com.example.ticket.core.application.dto.query.ZoneSeatsView;
import reactor.core.publisher.Mono;

import java.util.List;

public interface SeatQueryInPort {

    Mono<List<ZoneSeatsView>> execute(SeatQuery query);
}
