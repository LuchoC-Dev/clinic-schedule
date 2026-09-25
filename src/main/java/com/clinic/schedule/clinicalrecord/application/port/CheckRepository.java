package com.clinic.schedule.clinicalrecord.application.port;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import com.clinic.schedule.clinicalrecord.domain.Check;
import com.clinic.schedule.clinicalrecord.domain.CheckId;
import com.clinic.schedule.clinicalrecord.domain.ConsultationId;
import com.clinic.schedule.clinicalrecord.domain.PatientId;
import com.clinic.schedule.clinicalrecord.domain.PractitionerId;

/** Where checks are kept. A check is written once and later gains its result, nothing else (D-53). */
public interface CheckRepository {

    Optional<Check> findById(CheckId id);

    /** Every check of the patient, done in a consultation or not: part of their clinical record. */
    List<Check> findAllForPatient(PatientId patientId);

    /** Every check of the practice still waiting for its result (D-54). */
    List<Check> findAllPending();

    /** The checks done during that consultation, in the order they were done. */
    List<Check> findAllForConsultation(ConsultationId consultationId);

    /** When that practitioner last did a check to that patient, if ever: they saw the patient then (D-53). */
    Optional<LocalDateTime> findLastPerformedAt(PatientId patientId, PractitionerId practitionerId);

    /**
     * Stores a new check, or the result of one read in the same transaction.
     *
     * @throws com.clinic.schedule.clinicalrecord.domain.exception.ResultAlreadyRecordedException
     *         when someone else recorded a result for it meanwhile
     */
    void save(Check check);
}
