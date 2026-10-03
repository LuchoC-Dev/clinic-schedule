package com.clinic.schedule.portal.domain;

import java.time.LocalDateTime;

/**
 * The email that confirms an appointment booked on the web (D-82): to the account that booked it, naming the
 * patient, with the day, the time and the practitioner, and the link to cancel it (D-83). Never the reason,
 * which may be clinical, as the reminders never say it (D-78).
 *
 * @param cancelToken the token of the link to cancel it; null when there is no time left to, and the email
 *        says to call
 */
public record BookingConfirmation(String address, String fullName, LocalDateTime startsAt, String practitioner,
        String cancelToken) {
}
