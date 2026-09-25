package com.clinic.schedule.clinicalrecord.application.port;

import java.util.List;

import com.clinic.schedule.clinicalrecord.domain.AccessGrant;
import com.clinic.schedule.clinicalrecord.domain.PatientId;
import com.clinic.schedule.clinicalrecord.domain.PractitionerId;

/** Where access grants are kept: written once, and kept as a record of who opened whose history (D-54). */
public interface AccessGrantRepository {

    /** Every grant for the patient's record, to whoever, valid or not. */
    List<AccessGrant> findAllForPatient(PatientId patientId);

    /** The grants that practitioner holds for the patient's record, valid or not. */
    List<AccessGrant> findFor(PatientId patientId, PractitionerId practitionerId);

    void save(AccessGrant grant);
}
