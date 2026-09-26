package com.clinic.schedule.arrival.session.scheduling;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.clinic.schedule.arrival.session.command.CloseDueSessionsUseCase;

/**
 * Closes, every minute, the queues whose day is over (D-63). A trigger and nothing else: the rule
 * is the session's and the work the use case's. Switched off with {@code app.scheduling.enabled=false},
 * as the tests do.
 */
@Component
@ConditionalOnProperty(name = "app.scheduling.enabled", havingValue = "true", matchIfMissing = true)
class AutoCloseJob {

    private static final Logger log = LoggerFactory.getLogger(AutoCloseJob.class);

    private final CloseDueSessionsUseCase closeDue;

    AutoCloseJob(CloseDueSessionsUseCase closeDue) {
        this.closeDue = closeDue;
    }

    @Scheduled(fixedDelay = 60_000, initialDelay = 60_000)
    void run() {
        int closed = closeDue.handle();
        if (closed > 0) {
            log.info("Closed {} queue(s) at the end of their day", closed);
        }
    }
}
