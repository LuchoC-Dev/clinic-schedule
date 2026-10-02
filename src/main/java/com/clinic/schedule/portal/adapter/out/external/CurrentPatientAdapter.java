package com.clinic.schedule.portal.adapter.out.external;

import java.util.UUID;

import org.springframework.stereotype.Component;

import com.clinic.schedule.portal.application.port.CurrentPatientPort;
import com.clinic.schedule.security.api.CurrentPatientReader;

/** Answers {@link CurrentPatientPort} by asking the security module through its api (D-05). */
@Component
class CurrentPatientAdapter implements CurrentPatientPort {

    private final CurrentPatientReader currentPatient;

    CurrentPatientAdapter(CurrentPatientReader currentPatient) {
        this.currentPatient = currentPatient;
    }

    @Override
    public UUID patientId() {
        return currentPatient.patientId();
    }
}
