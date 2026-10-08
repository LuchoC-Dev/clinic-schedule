package com.clinic.schedule.cashdesk.application.port;

import java.util.UUID;

import com.clinic.schedule.cashdesk.domain.Insured;

/**
 * Whether a patient may go in covered (D-90): their health insurance and whether their identity was verified,
 * as they stand now. A {@code Port}: it is another module, not storage (D-18).
 */
public interface InsuredPatientPort {

    /** Unverified and with no insurance when no patient has that id. */
    Insured insuredOf(UUID patientId);
}
