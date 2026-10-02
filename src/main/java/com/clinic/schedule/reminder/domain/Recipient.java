package com.clinic.schedule.reminder.domain;

/**
 * Who a reminder goes to, and how it is to speak to them (D-78).
 *
 * @param greeted the name to greet; null for a patient under an alias, greeted without one
 * @param onBehalf whether the email is a responsible person's, so the reminder speaks of someone else's
 *        appointment
 * @param patientName the patient whose appointment it is, named to a responsible person; null on the
 *        patient's own email, and for a patient under an alias
 */
public record Recipient(String address, String greeted, boolean onBehalf, String patientName) {
}
