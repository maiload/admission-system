package com.example.ticket.gate;

import com.example.ticket.gate.config.GateProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(GateProperties.class)
public class QueueGateApplication {

    public static void main(String[] args) {
        SpringApplication.run(QueueGateApplication.class, args);
    }
}
