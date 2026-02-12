package com.example.ticket.admission.application.port.out;

import java.util.List;

public interface TokenGeneratorPort {

    List<String> generateBatch(String eventId, String scheduleId, int count);
}
