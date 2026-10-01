package com.clinic.schedule.patient.presentation.screen;

import com.clinic.schedule.patient.entity.Consent;
import com.clinic.schedule.patient.entity.Patient;
import com.clinic.schedule.patient.entity.Responsible;

/**
 * Who agreed to the patient being reminded on each channel, as their screen offers it (D-77): a channel
 * shows only when there is one, and offers its owner's consent only when it belongs to a responsible
 * person, named as the screen names them.
 *
 * @param phoneOwner the person the phone belongs to, such as "Madre: Ana Pérez"; null when it is the
 *        patient's own
 */
record ConsentEntry(boolean hasPhone, String phoneOwner, Consent phone, boolean hasEmail, String emailOwner,
        Consent email) {

    static ConsentEntry of(Patient patient) {
        return new ConsentEntry(
                patient.getPhone() != null, owner(patient.getPhone() == null ? null : patient.getPhone().owner()),
                patient.getPhoneConsent(),
                patient.getEmail() != null, owner(patient.getEmail() == null ? null : patient.getEmail().owner()),
                patient.getEmailConsent());
    }

    private static String owner(Responsible owner) {
        return owner == null ? null : DetailsEntry.relation(owner.relation()) + ": " + owner.name();
    }

    /** Who gave a consent, as the history of the details says it. */
    static String describe(Consent consent) {
        return switch (consent) {
            case PATIENT -> "Lo dio el paciente";
            case OWNER -> "Lo dio la persona responsable";
        };
    }
}
