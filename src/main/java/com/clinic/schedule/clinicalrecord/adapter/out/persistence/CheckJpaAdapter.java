package com.clinic.schedule.clinicalrecord.adapter.out.persistence;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Repository;

import com.clinic.schedule.clinicalrecord.application.port.CheckRepository;
import com.clinic.schedule.clinicalrecord.domain.Check;
import com.clinic.schedule.clinicalrecord.domain.CheckId;
import com.clinic.schedule.clinicalrecord.domain.ConsultationId;
import com.clinic.schedule.clinicalrecord.domain.PatientId;
import com.clinic.schedule.clinicalrecord.domain.PractitionerId;
import com.clinic.schedule.clinicalrecord.domain.exception.ResultAlreadyRecordedException;

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
    public List<Check> findAllForConsultation(ConsultationId consultationId) {
        return rows.findByConsultationIdOrderByPerformedAt(consultationId.value()).stream().map(CheckRow::toCheck).toList();
    }

    @Override
    public Optional<LocalDateTime> findLastPerformedAt(PatientId patientId, PractitionerId practitionerId) {
        return rows.findLastPerformedAt(patientId.value(), practitionerId.value());
    }

    /**
     * A new check is inserted whole. An existing one only ever gains its result (D-53): it is
     * written onto the row read earlier in the same transaction, whose version then says whether
     * someone else recorded a result meanwhile. It flushes at once, so that refusal is answered
     * here, as the {@link ResultAlreadyRecordedException} it means, and not at commit.
     */
    @Override
    public void save(Check check) {
        try {
            CheckRow row = rows.findById(check.id().value())
                    .map(existing -> {
                        existing.resultFrom(check);
                        return existing;
                    })
                    .orElseGet(() -> CheckRow.from(check));
            rows.saveAndFlush(row);
        } catch (ObjectOptimisticLockingFailureException e) {
            throw new ResultAlreadyRecordedException(check.id());
        }
    }
}
