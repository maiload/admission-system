package com.example.ticket.core.adapter.out.system;

import com.example.ticket.common.port.IdGeneratorPort;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class UuidGenerator implements IdGeneratorPort {

    @Override
    public String generateUuid() {
        return UUID.randomUUID().toString();
    }
}
