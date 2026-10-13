package com.clinic.schedule.clinicalrecord.adapter.out.persistence;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Repository;

import com.clinic.schedule.clinicalrecord.application.exception.RecordCopyChangedException;
import com.clinic.schedule.clinicalrecord.application.port.RecordCopyRepository;
import com.clinic.schedule.clinicalrecord.domain.CopyCode;
import com.clinic.schedule.clinicalrecord.domain.CopyDelivery;
import com.clinic.schedule.clinicalrecord.domain.PatientId;
import com.clinic.schedule.clinicalrecord.domain.RecordCopy;
import com.clinic.schedule.clinicalrecord.domain.RecordCopyId;

/** Implements the port over JPA. The only place that knows the copies' requests live in a table. */
@Repository
class RecordCopyJpaAdapter implements RecordCopyRepository {

    private final RecordCopyRowRepository rows;

    RecordCopyJpaAdapter(RecordCopyRowRepository rows) {
        this.rows = rows;
    }

    @Override
    public Optional<RecordCopy> findById(RecordCopyId id) {
        return rows.findById(id.value()).map(RecordCopyRow::toCopy);
    }

    @Override
    public Optional<RecordCopy> findByCode(CopyCode code) {
        return rows.findByCode(code.value()).map(RecordCopyRow::toCopy);
    }

    @Override
    public List<RecordCopy> findWaiting() {
        return rows.findByAuthenticatedAtIsNull().stream().map(RecordCopyRow::toCopy).toList();
    }

    @Override
    public List<RecordCopy> findAwaitingHandover() {
        return rows.findByDeliveryAndAuthenticatedAtIsNotNullAndHandedOverAtIsNull(CopyDelivery.PAPER).stream()
                .map(RecordCopyRow::toCopy)
                .toList();
    }

    @Override
    public List<RecordCopy> findForPatients(Collection<PatientId> patients) {
        return rows.findByPatientIdIn(patients.stream().map(PatientId::value).toList()).stream()
                .map(RecordCopyRow::toCopy)
                .toList();
    }

    @Override
    public boolean existsWaitingFor(PatientId patientId) {
        return rows.existsByPatientIdAndAuthenticatedAtIsNull(patientId.value());
    }

    /**
     * A new request is inserted whole; an existing one gains what happened to it, written onto the row read
     * earlier in the same transaction, whose version then says whether someone else changed it meanwhile. It
     * flushes at once, so that refusal is answered here and not at commit.
     */
    @Override
    public void save(RecordCopy copy) {
        try {
            RecordCopyRow row = rows.findById(copy.id().value())
                    .map(existing -> {
                        existing.changesFrom(copy);
                        return existing;
                    })
                    .orElseGet(() -> RecordCopyRow.from(copy));
            rows.saveAndFlush(row);
        } catch (ObjectOptimisticLockingFailureException e) {
            throw new RecordCopyChangedException(copy.id());
        }
    }
}
