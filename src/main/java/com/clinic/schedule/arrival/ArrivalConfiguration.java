package com.clinic.schedule.arrival;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

import com.clinic.schedule.arrival.session.domain.QueueSessionService;
import com.clinic.schedule.arrival.setup.domain.QueueSetupService;

/**
 * Wiring for the module. The domain services are declared here instead of being annotated, so the
 * domain keeps no reference to Spring (D-16, D-62), and what may change comes from configuration.
 * Scheduling is enabled here too, since this module closes its queues at the end of their day
 * (D-63).
 */
@Configuration
@EnableScheduling
class ArrivalConfiguration {

    @Bean
    QueueSetupService queueSetupService() {
        return new QueueSetupService();
    }

    /** How many calls a patient gets without coming in before they are taken out of the queue (D-63). */
    @Bean
    QueueSessionService queueSessionService(@Value("${app.arrival.calls-before-no-show}") int callsBeforeNoShow) {
        return new QueueSessionService(callsBeforeNoShow);
    }
}
