package com.clinic.schedule.reminder.domain;

import java.time.LocalDateTime;

/**
 * What a reminder says, before it is put into words (D-78): who it goes to, when the appointment is and
 * with whom, and the link to cancel it (D-83). Never the reason for the appointment, which may be clinical.
 *
 * @param cancelLink the address of the link to cancel it; null when there is no time left to, and the
 *        reminder says to call
 */
public record ReminderMessage(Recipient recipient, LocalDateTime startsAt, String practitioner, String cancelLink) {
}
