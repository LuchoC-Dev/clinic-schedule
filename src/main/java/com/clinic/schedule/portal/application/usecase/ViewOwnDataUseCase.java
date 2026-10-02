package com.clinic.schedule.portal.application.usecase;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.clinic.schedule.portal.application.port.CurrentPatientPort;
import com.clinic.schedule.portal.application.port.PatientDataPort;
import com.clinic.schedule.portal.domain.OwnData;

/**
 * The data of the patient logged in (D-81). Whose is never asked: it is whoever is logged in, so no patient
 * can read another's.
 */
@Service
public class ViewOwnDataUseCase {

    private final CurrentPatientPort currentPatient;
    private final PatientDataPort patients;

    public ViewOwnDataUseCase(CurrentPatientPort currentPatient, PatientDataPort patients) {
        this.currentPatient = currentPatient;
        this.patients = patients;
    }

    @Transactional(readOnly = true)
    public OwnData handle() {
        return patients.of(currentPatient.patientId())
                .orElseThrow(() -> new IllegalStateException("The patient of a web account is missing"));
    }
}
