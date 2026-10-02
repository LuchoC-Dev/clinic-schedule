package com.clinic.schedule.reminder.domain;

import java.time.LocalDate;
import java.time.Period;

/**
 * Whether a patient is to be reminded of an appointment, and how (D-78). Pure: it is handed what is
 * known of the patient and the appointment's day, and reads nothing.
 *
 * <ul>
 *   <li>No email, nothing to send; nobody agreed, nothing is sent.</li>
 *   <li>A responsible person's email that only its owner agreed to is used for a patient under 13: from
 *       13 an adolescent decides alone on much of their own care (Código Civil y Comercial, art. 26), and
 *       a reminder could tell a parent of a visit they keep to themselves. Without a date of birth the
 *       patient may be that adolescent, so it is not used either.</li>
 *   <li>The patient's own agreement is enough on any of their channels, a responsible person's
 *       included: an adult may be reminded at their daughter's email because they said so.</li>
 * </ul>
 *
 * <p>The age is the one the patient will have on the day of the appointment: that is the visit the
 * reminder speaks of.
 */
public final class ReminderPolicy {

    /** From this age an adolescent decides alone on treatments that are not invasive (CCyC, art. 26). */
    static final int CONFIDENTIAL_FROM_AGE = 13;

    private ReminderPolicy() {
    }

    public static Decision decide(Contact contact, LocalDate appointmentDay) {
        if (contact.address() == null) {
            return Decision.notSent(NotSentReason.NO_EMAIL);
        }
        if (contact.consent() == null) {
            return Decision.notSent(NotSentReason.NO_CONSENT);
        }
        boolean onBehalf = contact.ownerName() != null;
        if (onBehalf && contact.consent() == Consent.OWNER && !isAChild(contact.birthDate(), appointmentDay)) {
            return Decision.notSent(NotSentReason.CONFIDENTIAL);
        }
        return Decision.send(onBehalf
                ? new Recipient(contact.address(), contact.ownerName(), true, contact.patientName())
                : new Recipient(contact.address(), contact.patientName(), false, null));
    }

    private static boolean isAChild(LocalDate birthDate, LocalDate on) {
        return birthDate != null && Period.between(birthDate, on).getYears() < CONFIDENTIAL_FROM_AGE;
    }

    /** Either who to send the reminder to, or why it is not sent. */
    public record Decision(Recipient recipient, NotSentReason notSent) {

        static Decision send(Recipient recipient) {
            return new Decision(recipient, null);
        }

        static Decision notSent(NotSentReason reason) {
            return new Decision(null, reason);
        }
    }
}
