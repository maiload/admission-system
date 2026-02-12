package com.example.ticket.core.config;

import com.example.ticket.core.adapter.out.token.HmacTokenSigner;
import com.example.ticket.core.application.port.out.TokenSignerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.scripting.support.ResourceScriptSource;

@Configuration
@EnableConfigurationProperties(CoreProperties.class)
public class CoreConfig {

    @Bean
    public RedisScript<String> handshakeScript() {
        DefaultRedisScript<String> script = new DefaultRedisScript<>();
        script.setScriptSource(new ResourceScriptSource(new ClassPathResource("lua/core-handshake.lua")));
        script.setResultType(String.class);
        return script;
    }

    @Bean
    public TokenSignerPort tokenSignerPort(CoreProperties coreProperties) {
        return new HmacTokenSigner(coreProperties);
    }
}
