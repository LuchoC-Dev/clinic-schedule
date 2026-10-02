package com.clinic.schedule.reminder.application.port;

import com.clinic.schedule.reminder.domain.ReminderMessage;

/**
 * Sends a reminder (D-78). Email is the only adapter so far; WhatsApp or SMS would be one more, and the
 * rules of who is reminded would not change.
 */
public interface ReminderSenderPort {

    /** @throws RuntimeException when it did not go out; it is recorded as failed, and not tried again */
    void send(ReminderMessage message);
}
