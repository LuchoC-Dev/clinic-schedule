package com.clinic.schedule.portal.adapter.in.scheduling;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.clinic.schedule.portal.application.usecase.RegisterOnWebUseCase;

/**
 * Forgets, every hour, the registrations made on the web that expired never confirmed (D-91), so the data of
 * someone who never became a patient is not kept. A trigger and nothing else: an expired registration confirms
 * nothing whether or not this ran. Off with the rest of the jobs, {@code app.scheduling.enabled=false}, as the tests
 * do.
 */
@Component
@ConditionalOnProperty(name = "app.scheduling.enabled", havingValue = "true", matchIfMissing = true)
class ForgetRegistrationsJob {

    private static final Logger log = LoggerFactory.getLogger(ForgetRegistrationsJob.class);

    private final RegisterOnWebUseCase registrations;

    ForgetRegistrationsJob(RegisterOnWebUseCase registrations) {
        this.registrations = registrations;
    }

    @Scheduled(fixedDelay = 3_600_000, initialDelay = 60_000)
    void run() {
        int forgotten = registrations.forgetExpired();
        if (forgotten > 0) {
            log.info("Registrations on the web: {} expired never confirmed, forgotten", forgotten);
        }
    }
}
