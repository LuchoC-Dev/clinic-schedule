package com.clinic.schedule.portal.application.port;

import java.util.Optional;
import java.util.UUID;

import com.clinic.schedule.portal.application.exception.OwnDataRefusedException;
import com.clinic.schedule.portal.domain.OwnContact;
import com.clinic.schedule.portal.domain.OwnData;

/**
 * A patient's own data, kept by the patient module (D-81): read, and changed with that module's rules, which
 * this module does not repeat.
 */
public interface PatientDataPort {

    Optional<OwnData> of(UUID patientId);

    /** @throws OwnDataRefusedException when a value cannot be right; nothing is changed then */
    void changeContact(UUID patientId, OwnContact contact);

    /**
     * True is the patient's own consent, false withdraws any, null leaves the channel as it is.
     *
     * @throws OwnDataRefusedException when they agree on a channel they have no number or address for
     */
    void changeConsent(UUID patientId, Boolean phone, Boolean email);
}
