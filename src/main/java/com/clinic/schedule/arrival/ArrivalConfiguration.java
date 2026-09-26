package com.clinic.schedule.arrival;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.clinic.schedule.arrival.setup.domain.QueueSetupService;

/**
 * Wiring for the module. The domain service is declared here instead of being annotated, so the
 * domain keeps no reference to Spring (D-16, D-62).
 */
@Configuration
class ArrivalConfiguration {

    @Bean
    QueueSetupService queueSetupService() {
        return new QueueSetupService();
    }
}
