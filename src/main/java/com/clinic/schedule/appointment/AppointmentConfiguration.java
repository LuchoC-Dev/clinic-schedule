package com.clinic.schedule.appointment;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

import com.clinic.schedule.appointment.domain.BookingService;

/**
 * Wiring for the module. The domain service is declared here instead of being annotated,
 * so the domain keeps no reference to Spring (D-16).
 *
 * <p>Scheduling is enabled here because this module is the one that needs it, for the
 * sweep that marks absent the patients who never came (D-06, D-61).
 */
@Configuration
@EnableScheduling
class AppointmentConfiguration {

    @Bean
    BookingService bookingService() {
        return new BookingService();
    }
}
