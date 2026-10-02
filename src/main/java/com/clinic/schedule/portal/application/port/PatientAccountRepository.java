package com.clinic.schedule.portal.application.port;

import java.util.Optional;
import java.util.UUID;

import com.clinic.schedule.portal.domain.PatientAccount;

/** Where web accounts are kept. */
public interface PatientAccountRepository {

    void save(PatientAccount account);

    Optional<PatientAccount> findById(UUID id);

    /** The patient's account, in any state: a patient has one at most. */
    Optional<PatientAccount> findByPatientId(UUID patientId);
}
