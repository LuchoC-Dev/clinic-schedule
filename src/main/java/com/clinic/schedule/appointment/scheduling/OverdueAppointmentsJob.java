package com.clinic.schedule.appointment.scheduling;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.clinic.schedule.appointment.application.CancelOverdueAppointmentsUseCase;
import com.clinic.schedule.appointment.domain.Appointment;

/**
 * Runs the automatic cancellation every minute (D-06). A trigger and nothing else: the rule
 * is in the appointment and the work in the use case, so this class holds no decision worth
 * testing on its own.
 *
 * <p>It can be switched off with {@code app.scheduling.enabled=false}. The tests do, because
 * they book appointments on fixed past dates, which a real clock would find overdue and cancel
 * in the middle of a test.
 */
@Component
@ConditionalOnProperty(name = "app.scheduling.enabled", havingValue = "true", matchIfMissing = true)
class OverdueAppointmentsJob {

    private static final Logger log = LoggerFactory.getLogger(OverdueAppointmentsJob.class);

    private final CancelOverdueAppointmentsUseCase cancelOverdue;

    OverdueAppointmentsJob(CancelOverdueAppointmentsUseCase cancelOverdue) {
        this.cancelOverdue = cancelOverdue;
    }

    @Scheduled(fixedDelay = 60_000, initialDelay = 60_000)
    void run() {
        List<Appointment> cancelled = cancelOverdue.handle();
        if (!cancelled.isEmpty()) {
            log.info("Cancelled {} overdue appointment(s) whose patient never arrived", cancelled.size());
        }
    }
}
