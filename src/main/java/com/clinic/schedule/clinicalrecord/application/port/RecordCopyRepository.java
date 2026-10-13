package com.clinic.schedule.clinicalrecord.application.port;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import com.clinic.schedule.clinicalrecord.domain.CopyCode;
import com.clinic.schedule.clinicalrecord.domain.PatientId;
import com.clinic.schedule.clinicalrecord.domain.RecordCopy;
import com.clinic.schedule.clinicalrecord.domain.RecordCopyId;

/** Where the requests for a copy of a record are kept, every one of them, whatever became of it (D-95). */
public interface RecordCopyRepository {

    Optional<RecordCopy> findById(RecordCopyId id);

    Optional<RecordCopy> findByCode(CopyCode code);

    /** Those not authenticated yet, of any patient. */
    List<RecordCopy> findWaiting();

    /** Those on paper, authenticated and not handed over: the envelopes at reception. */
    List<RecordCopy> findAwaitingHandover();

    /** Every request of these patients' records. */
    List<RecordCopy> findForPatients(Collection<PatientId> patients);

    /** Whether the patient's record has a copy waiting to be authenticated. */
    boolean existsWaitingFor(PatientId patientId);

    /**
     * @throws com.clinic.schedule.clinicalrecord.application.exception.RecordCopyChangedException when someone
     *         else changed it meanwhile
     */
    void save(RecordCopy copy);
}
