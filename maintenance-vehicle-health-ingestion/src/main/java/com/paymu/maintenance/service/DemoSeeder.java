package com.paymu.maintenance.service;

import com.paymu.maintenance.controller.MaintenanceController;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * Seeds synthetic maintenance data on application startup if demo.seed-on-startup is true.
 */
@Component
public class DemoSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoSeeder.class);

    private final MaintenanceController controller;

    @Value("${demo.seed-on-startup:false}")
    private boolean seedOnStartup;

    public DemoSeeder(MaintenanceController controller) {
        this.controller = controller;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (seedOnStartup) {
            log.info("demo.seed-on-startup is enabled. Seeding maintenance demo data...");
            controller.generateSynthetic();
            log.info("Maintenance demo data seeded successfully on startup.");
        }
    }
}
