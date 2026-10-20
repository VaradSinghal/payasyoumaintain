package com.paymu.identity;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

import java.time.Clock;
import java.time.ZoneId;

@SpringBootApplication
public class IdentityConsentApplication {
    public static void main(String[] args) {
        SpringApplication.run(IdentityConsentApplication.class, args);
    }

    @Bean
    public Clock clock(@Value("${identity.timezone:Asia/Kolkata}") String timezone) {
        return Clock.system(ZoneId.of(timezone));
    }
}
