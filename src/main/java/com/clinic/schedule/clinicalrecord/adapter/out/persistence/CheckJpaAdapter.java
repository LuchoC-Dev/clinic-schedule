package com.clinic.schedule.clinicalrecord.adapter.out.persistence;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Repository;

import com.clinic.schedule.clinicalrecord.application.exception.CheckChangedException;
import com.clinic.schedule.clinicalrecord.application.port.CheckRepository;
import com.clinic.schedule.clinicalrecord.domain.Check;
import com.clinic.schedule.clinicalrecord.domain.CheckId;
import com.clinic.schedule.clinicalrecord.domain.ConsultationId;
import com.clinic.schedule.clinicalrecord.domain.PatientId;
import com.clinic.schedule.clinicalrecord.domain.PractitionerId;

/** Implements the port over JPA. The only place that knows checks live in a table. */
@Repository
class CheckJpaAdapter implements CheckRepository {

    private final CheckRowRepository rows;

    CheckJpaAdapter(CheckRowRepository rows) {
        this.rows = rows;
    }

    @Override
    public Optional<Check> findById(CheckId id) {
        return rows.findById(id.value()).map(CheckRow::toCheck);
    }

    @Override
    public List<Check> findAllForPatient(PatientId patientId) {
        return rows.findByPatientId(patientId.value()).stream().map(CheckRow::toCheck).toList();
    }

    @Override
    public List<Check> findAllPending() {
        return rows.findByResultAuthorIsNull().stream().map(CheckRow::toCheck).toList();
    }

    @Override
    public List<Check> findAllForConsultation(ConsultationId consultationId) {
        return rows.findByConsultationIdOrderByPerformedAt(consultationId.value()).stream().map(CheckRow::toCheck).toList();
    }

    @Override
    public Optional<LocalDateTime> findLastPerformedAt(PatientId patientId, PractitionerId practitionerId) {
        return rows.findLastPerformedAt(patientId.value(), practitionerId.value());
    }

    /**
     * A new check is inserted whole. An existing one only ever gains its result and addenda (D-53,
     * D-58): they are written onto the row read earlier in the same transaction, whose version then
     * says whether someone else changed it meanwhile. It flushes at once, so that refusal is
     * answered here, as a {@link CheckChangedException}, and not at commit.
     */
    @Override
    public void save(Check check) {
        try {
            CheckRow row = rows.findById(check.id().value())
                    .map(existing -> {
                        existing.changesFrom(check);
                        return existing;
                    })
                    .orElseGet(() -> CheckRow.from(check));
            rows.saveAndFlush(row);
        } catch (ObjectOptimisticLockingFailureException e) {
            throw new CheckChangedException(check.id());
        }
    }
}
