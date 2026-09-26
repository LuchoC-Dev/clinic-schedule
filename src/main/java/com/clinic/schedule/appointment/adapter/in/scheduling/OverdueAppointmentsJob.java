package com.clinic.schedule.appointment.adapter.in.scheduling;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.clinic.schedule.appointment.application.usecase.MarkAbsentAppointmentsUseCase;
import com.clinic.schedule.appointment.domain.Appointment;

/**
 * Marks absent, every minute, the appointments whose patient never arrived (D-06, D-61). A
 * trigger and nothing else: the rule is in the appointment and the work in the use case, so this
 * class holds no decision worth testing on its own.
 *
 * <p>It can be switched off with {@code app.scheduling.enabled=false}. The tests do, because
 * they book appointments on fixed past dates, which a real clock would find overdue and mark
 * absent in the middle of a test.
 */
@Component
@ConditionalOnProperty(name = "app.scheduling.enabled", havingValue = "true", matchIfMissing = true)
class OverdueAppointmentsJob {

    private static final Logger log = LoggerFactory.getLogger(OverdueAppointmentsJob.class);

    private final MarkAbsentAppointmentsUseCase markAbsent;

    OverdueAppointmentsJob(MarkAbsentAppointmentsUseCase markAbsent) {
        this.markAbsent = markAbsent;
    }

    @Scheduled(fixedDelay = 60_000, initialDelay = 60_000)
    void run() {
        List<Appointment> absent = markAbsent.handle();
        if (!absent.isEmpty()) {
            log.info("Marked {} appointment(s) absent: their patient never arrived", absent.size());
        }
    }
}
