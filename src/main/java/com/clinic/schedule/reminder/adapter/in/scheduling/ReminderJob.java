package com.clinic.schedule.reminder.adapter.in.scheduling;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.clinic.schedule.reminder.application.usecase.SendRemindersUseCase;
import com.clinic.schedule.reminder.domain.Reminder;

/**
 * Sends the reminders once a day, at the time of {@code app.reminders.cron} in the practice's time zone
 * — 10:00 by default (D-78). A trigger and nothing else: who is reminded is the policy's, and the work the
 * use case's. Off with the rest of the jobs, {@code app.scheduling.enabled=false}, as the tests do.
 */
@Component
@ConditionalOnProperty(name = "app.scheduling.enabled", havingValue = "true", matchIfMissing = true)
class ReminderJob {

    private static final Logger log = LoggerFactory.getLogger(ReminderJob.class);

    private final SendRemindersUseCase sendReminders;

    ReminderJob(SendRemindersUseCase sendReminders) {
        this.sendReminders = sendReminders;
    }

    @Scheduled(cron = "${app.reminders.cron}", zone = "${app.time-zone}")
    void run() {
        List<Reminder> recorded = sendReminders.handle();
        log.info("Reminders: {} appointment(s) considered", recorded.size());
    }
}
