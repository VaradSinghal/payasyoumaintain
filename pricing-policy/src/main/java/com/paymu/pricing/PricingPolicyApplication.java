package com.paymu.pricing;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

import java.time.Clock;
import java.time.ZoneId;

@SpringBootApplication
public class PricingPolicyApplication {
    public static void main(String[] args) {
        SpringApplication.run(PricingPolicyApplication.class, args);
    }

    @Bean
    public Clock clock(@Value("${pricing.timezone:Asia/Kolkata}") String timezone) {
        return Clock.system(ZoneId.of(timezone));
    }
}
