package com.clinic.schedule.portal.domain;

import java.time.LocalDateTime;

/**
 * The email that confirms an appointment booked on the web (D-82), or moved there to a new time (D-93): to the
 * account that booked or moved it, naming the patient, with the day, the time and the practitioner, and the link
 * to cancel it (D-83). Never the reason, which may be clinical, as the reminders never say it (D-78).
 *
 * @param fullName whose account booked it, whom the email greets
 * @param member the member of their family it was booked for (D-85); null when it is their own
 * @param cancelToken the token of the link to cancel it; null when there is no time left to, and the email
 *        says to call
 * @param moved whether it confirms a move, {@code startsAt} being the new time; false for a booking
 */
public record BookingConfirmation(String address, String fullName, String member, LocalDateTime startsAt,
        String practitioner, String cancelToken, boolean moved) {
}
