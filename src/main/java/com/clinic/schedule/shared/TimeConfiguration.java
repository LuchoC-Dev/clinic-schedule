package com.clinic.schedule.shared;

import java.time.Clock;
import java.time.ZoneId;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * The practice's clock, in its fixed time zone (D-10). Code that needs "now" takes this
 * {@link Clock} instead of calling {@code LocalDateTime.now()}, so tests can fix the time.
 *
 * <p>It arrives with the first code that reads the time: the check-in and the automatic
 * cancellation (D-14).
 */
@Configuration
class TimeConfiguration {

    @Bean
    Clock clock(@Value("${app.time-zone}") String timeZone) {
        return Clock.system(ZoneId.of(timeZone));
    }
}
