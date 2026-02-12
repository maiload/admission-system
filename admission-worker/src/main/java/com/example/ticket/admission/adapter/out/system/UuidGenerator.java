package com.example.ticket.admission.adapter.out.system;

import com.example.ticket.common.port.IdGeneratorPort;

import java.util.UUID;

public class UuidGenerator implements IdGeneratorPort {

    @Override
    public String generateUuid() {
        return UUID.randomUUID().toString();
    }
}
