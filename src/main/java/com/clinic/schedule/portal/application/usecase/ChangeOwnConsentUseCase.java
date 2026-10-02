package com.clinic.schedule.portal.application.usecase;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.clinic.schedule.portal.application.port.CurrentPatientPort;
import com.clinic.schedule.portal.application.port.PatientDataPort;

/**
 * The patient logged in says, on each of their channels, whether they agree to be reminded there (D-81):
 * true is their own consent, false withdraws any — a responsible person's too —, null leaves it as it is.
 */
@Service
public class ChangeOwnConsentUseCase {

    private final CurrentPatientPort currentPatient;
    private final PatientDataPort patients;

    public ChangeOwnConsentUseCase(CurrentPatientPort currentPatient, PatientDataPort patients) {
        this.currentPatient = currentPatient;
        this.patients = patients;
    }

    /** @throws com.clinic.schedule.portal.application.exception.OwnDataRefusedException when it cannot be */
    @Transactional
    public void handle(Boolean phone, Boolean email) {
        patients.changeConsent(currentPatient.patientId(), phone, email);
    }
}
