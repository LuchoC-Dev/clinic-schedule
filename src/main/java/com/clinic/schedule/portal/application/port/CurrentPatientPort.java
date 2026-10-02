package com.clinic.schedule.portal.application.port;

import java.util.UUID;

/** Whose web account is asking: the patient logged in, as the security module knows them (D-80). */
public interface CurrentPatientPort {

    UUID patientId();
}
