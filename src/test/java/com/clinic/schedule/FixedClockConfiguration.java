package com.clinic.schedule;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * The practice's clock, stopped at the start of Tuesday 10 March 2026 for every test that
 * starts the application.
 *
 * <p>Booking refuses a time already gone and one more than a year ahead, and a check-in only
 * happens on the day of the appointment (D-38). With the real clock, tests booking a fixed date
 * would start failing the day that date passed. Stopped at midnight, the whole of that Tuesday
 * is still ahead, and it is also "today" for the check-in.
 *
 * <p>Picked up by component scanning, being in the application's package, and preferred over
 * the real one by {@code @Primary}.
 */
@Configuration
public class FixedClockConfiguration {

    public static final LocalDateTime NOW = LocalDateTime.of(2026, 3, 10, 0, 0);

    @Bean
    @Primary
    Clock fixedClock() {
        ZoneId zone = ZoneId.of("America/Argentina/Buenos_Aires");
        return Clock.fixed(NOW.atZone(zone).toInstant(), zone);
    }
}
