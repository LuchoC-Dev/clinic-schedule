package com.clinic.schedule.patient.api;

import java.util.UUID;

/**
 * Registers patients for other modules, and changes what a patient may change of their own from their web
 * account. Kept apart from {@link PatientReader} because it writes, and the name says so (D-18).
 *
 * <p>Its callers join the registration to their own transaction: booking a new patient registers
 * them and books them at once, so a refused booking leaves no patient behind (D-37, D-75), and the
 * arrival queue registers a patient who gives no name as they come in (D-63).
 */
public interface PatientWriter {

    /**
     * @return the new patient's id
     * @throws PatientRegistrationRefusedException when the name is missing, the document is
     *         incomplete, malformed or already someone else's, or the phone or the email cannot be
     *         right; nothing is registered then
     */
    UUID register(NewPatient patient);

    /**
     * A patient who gives no name, under a new alias such as NN-4821 (D-60). Its caller is the
     * arrival queue, where such a patient comes in (D-63).
     *
     * @return the new patient's id
     */
    UUID registerUnidentified();

    /**
     * Whether an adult registering on the web gave what a patient may be registered with (D-91), without
     * registering anyone: the registration waits for its confirmation by email, and should not fail then for a
     * value that could be told now. Whether the document is someone's already is not looked at.
     *
     * @throws PatientRegistrationRefusedException when the name or the document is missing or cannot be one, or
     *         the date of birth or the phone cannot be right
     */
    void checkFromWeb(WebPatient patient);

    /**
     * An adult who registered on the web and confirmed it from their email (D-91): their identity declared, since
     * nobody has seen their document yet, and marked as registered on the web. It joins the caller's transaction,
     * so the account opened with it goes back with the patient if it fails.
     *
     * @return the new patient's id
     * @throws PatientRegistrationRefusedException as {@link #checkFromWeb}, and when the document is someone's
     *         already; nothing is registered then
     */
    UUID registerFromWeb(WebPatient patient);

    /**
     * The patient changes their own contact and health insurance (D-81), with reception's rules: a new
     * number, address or owner loses its consent, and every change goes to the history, by whoever is logged
     * in. The date of birth stays as it was. It joins the caller's transaction.
     *
     * @throws PatientChangeRefusedException when a value cannot be right; nothing is changed then
     */
    void changeContact(UUID patientId, ContactChange change);

    /**
     * The patient says, on each channel, whether they agree to be reminded there (D-81): true is their own
     * consent, false withdraws any — a responsible person's too —, and null leaves it as it is.
     *
     * @throws PatientChangeRefusedException when they agree on a channel they have no number or address for
     */
    void changeOwnConsent(UUID patientId, Boolean phone, Boolean email);
}
