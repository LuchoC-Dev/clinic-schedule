package com.clinic.schedule.appointment;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.clinic.schedule.appointment.domain.BookingService;

/**
 * Wiring for the module. The domain service is declared here instead of being annotated,
 * so the domain keeps no reference to Spring (D-16).
 */
@Configuration
class AppointmentConfiguration {

    @Bean
    BookingService bookingService() {
        return new BookingService();
    }
}
