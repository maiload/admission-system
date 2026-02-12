package com.example.ticket.core;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@SpringBootApplication
public class TicketingCoreApplication {

    public static void main(String[] args) {
        SpringApplication.run(TicketingCoreApplication.class, args);
    }
}
