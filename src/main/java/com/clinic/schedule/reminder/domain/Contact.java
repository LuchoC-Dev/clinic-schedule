package com.clinic.schedule.reminder.domain;

import java.time.LocalDate;

/**
 * What reminding a patient needs to know of them (D-78): their name, their date of birth and their
 * email, with whose it is and who agreed to its use.
 *
 * @param patientName null for a patient under an alias, who is not to be named
 * @param birthDate null when not given
 * @param address null when the patient has no email
 * @param ownerName the responsible person the email belongs to; null when it is the patient's own
 * @param consent who agreed to the patient being reminded there; null for nobody
 */
public record Contact(String patientName, LocalDate birthDate, String address, String ownerName, Consent consent) {

    /** A patient nothing is known of: the patient module did not answer for them. */
    public static final Contact UNKNOWN = new Contact(null, null, null, null, null);
}
