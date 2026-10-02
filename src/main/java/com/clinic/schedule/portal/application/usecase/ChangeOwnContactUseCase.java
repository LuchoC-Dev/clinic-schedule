package com.clinic.schedule.portal.application.usecase;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.clinic.schedule.portal.application.port.CurrentPatientPort;
import com.clinic.schedule.portal.application.port.PatientDataPort;
import com.clinic.schedule.portal.domain.OwnContact;

/**
 * The patient logged in changes their contact and health insurance (D-81), with the rules the patient
 * module keeps for reception's changes. Their own alone: whose comes from the login.
 */
@Service
public class ChangeOwnContactUseCase {

    private final CurrentPatientPort currentPatient;
    private final PatientDataPort patients;

    public ChangeOwnContactUseCase(CurrentPatientPort currentPatient, PatientDataPort patients) {
        this.currentPatient = currentPatient;
        this.patients = patients;
    }

    /** @throws com.clinic.schedule.portal.application.exception.OwnDataRefusedException when it cannot be */
    @Transactional
    public void handle(OwnContact contact) {
        patients.changeContact(currentPatient.patientId(), contact);
    }
}
