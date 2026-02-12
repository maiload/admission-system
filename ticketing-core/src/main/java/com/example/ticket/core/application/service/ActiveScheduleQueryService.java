package com.example.ticket.core.application.service;

import com.example.ticket.core.application.port.in.ActiveScheduleQueryInPort;
import com.example.ticket.core.application.port.out.ActiveSchedulePort;
import com.example.ticket.core.application.port.out.ScheduleReadPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ActiveScheduleQueryService implements ActiveScheduleQueryInPort {

    private final ActiveSchedulePort activeSchedulePort;
    private final ScheduleReadPort scheduleReadPort;

    @Override
    public Flux<ActiveScheduleView> findActiveSchedules() {
        return activeSchedulePort.findAll()
                .collectList()
                .flatMapMany(active -> {
                    if (active.isEmpty()) {
                        return Flux.empty();
                    }
                    List<UUID> scheduleIds = active.stream()
                            .map(a -> UUID.fromString(a.scheduleId()))
                            .toList();
                    Map<UUID, String> eventByScheduleId = active.stream()
                            .collect(Collectors.toMap(
                                    a -> UUID.fromString(a.scheduleId()),
                                    ActiveSchedulePort.ActiveSchedule::eventId
                            ));

                    return scheduleReadPort.findDetailsByIds(scheduleIds)
                            .map(detail -> toView(detail, eventByScheduleId));
                });
    }

    private ActiveScheduleView toView(
            ScheduleReadPort.ScheduleDetailView detail,
            Map<UUID, String> eventByScheduleId
    ) {
        UUID eventId = UUID.fromString(
                eventByScheduleId.getOrDefault(detail.scheduleId(), detail.eventId().toString()));
        return new ActiveScheduleView(
                eventId,
                detail.scheduleId(),
                detail.trainName(),
                detail.trainNumber(),
                detail.departure(),
                detail.arrival(),
                detail.departureTime(),
                detail.arrivalTime(),
                detail.serviceDate(),
                detail.price()
        );
    }
}
