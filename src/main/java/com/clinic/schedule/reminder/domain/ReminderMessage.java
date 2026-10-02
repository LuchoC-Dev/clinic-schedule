package com.clinic.schedule.reminder.domain;

import java.time.LocalDateTime;

/**
 * What a reminder says, before it is put into words (D-78): who it goes to, when the appointment is and
 * with whom. Never the reason for the appointment, which may be clinical.
 */
public record ReminderMessage(Recipient recipient, LocalDateTime startsAt, String practitioner) {
}
