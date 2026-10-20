package com.paymu.claims;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import org.springframework.context.annotation.Bean;

import java.time.Clock;

@SpringBootApplication
public class ClaimsVerificationApplication {
    public static void main(String[] args) {
        SpringApplication.run(ClaimsVerificationApplication.class, args);
    }

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
