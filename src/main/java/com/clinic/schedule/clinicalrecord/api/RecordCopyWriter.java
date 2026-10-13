package com.clinic.schedule.clinicalrecord.api;

import java.util.UUID;

/**
 * Asks for a copy of a patient's whole clinical record (D-95), for the patients' web. Who may ask for whose is the
 * caller's to decide — the patient from 13, or a parent with the relation proved —: this module keeps the request,
 * puts the copy together and has it authenticated.
 */
public interface RecordCopyWriter {

    /**
     * @param requestedBy the patient who asks
     * @param requestedByAccount the name the asking account goes by wherever something records who did it
     * @return the request
     * @throws RecordCopyAlreadyRequestedException when the record already has a copy waiting to be authenticated
     */
    RecordCopySummary request(UUID patientId, UUID requestedBy, String requestedByAccount, CopyDeliveryChoice delivery);
}
