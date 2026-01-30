package com.example.ticket.core.config;

import com.example.ticket.common.port.ClockPort;
import com.example.ticket.common.port.IdGeneratorPort;
import com.example.ticket.core.application.command.ConfirmHoldUseCase;
import com.example.ticket.core.application.command.CreateHoldUseCase;
import com.example.ticket.core.application.command.EnterCoreUseCase;
import com.example.ticket.core.application.port.out.*;
import com.example.ticket.core.application.query.SeatQueryUseCase;
import com.example.ticket.core.domain.hold.HoldPolicy;
import com.example.ticket.core.domain.service.SeatAvailabilityService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.scripting.support.ResourceScriptSource;

@Configuration
public class CoreConfig {

    @Value("${core.enter-token.secret}")
    private String enterTokenSecret;

    @Value("${core.session.secret}")
    private String coreSessionSecret;

    @Value("${core.session.ttl-sec}")
    private int sessionTtlSec;

    @Value("${core.hold.ttl-sec}")
    private int holdTtlSec;

    @Value("${core.soldout.ttl-sec}")
    private long soldOutTtlSec;

    @Bean
    public HoldPolicy holdPolicy() {
        return new HoldPolicy(holdTtlSec);
    }

    @Bean
    public SeatAvailabilityService seatAvailabilityService() {
        return new SeatAvailabilityService();
    }

    @Bean
    public RedisScript<String> handshakeScript() {
        DefaultRedisScript<String> script = new DefaultRedisScript<>();
        script.setScriptSource(new ResourceScriptSource(new ClassPathResource("lua/core-handshake.lua")));
        script.setResultType(String.class);
        return script;
    }

    @Bean
    public int sessionTtlSec() {
        return sessionTtlSec;
    }

    @Bean
    public EnterCoreUseCase enterCoreUseCase(SessionPort sessionPort, IdGeneratorPort idGenerator) {
        return new EnterCoreUseCase(sessionPort, idGenerator,
                enterTokenSecret, coreSessionSecret, sessionTtlSec);
    }

    @Bean
    public CreateHoldUseCase createHoldUseCase(HoldRepositoryPort holdRepo, SeatQueryPort seatQuery,
                                                SoldOutPort soldOutPort, HoldPolicy holdPolicy,
                                                SeatAvailabilityService availSvc,
                                                ClockPort clock, IdGeneratorPort idGen) {
        return new CreateHoldUseCase(holdRepo, seatQuery, soldOutPort, holdPolicy, availSvc,
                clock, idGen, "a0000000-0000-0000-0000-000000000001", soldOutTtlSec);
    }

    @Bean
    public ConfirmHoldUseCase confirmHoldUseCase(HoldRepositoryPort holdRepo,
                                                  ReservationRepositoryPort reservationRepo,
                                                  SeatQueryPort seatQuery,
                                                  SessionPort sessionPort,
                                                  SoldOutPort soldOutPort,
                                                  SeatAvailabilityService availSvc,
                                                  ClockPort clock, IdGeneratorPort idGen) {
        return new ConfirmHoldUseCase(holdRepo, reservationRepo, seatQuery, sessionPort, soldOutPort,
                availSvc, clock, idGen, "a0000000-0000-0000-0000-000000000001", soldOutTtlSec);
    }

    @Bean
    public SeatQueryUseCase seatQueryUseCase(SeatQueryPort seatQuery, SessionPort sessionPort) {
        return new SeatQueryUseCase(seatQuery, sessionPort);
    }
}
