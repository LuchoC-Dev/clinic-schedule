package com.clinic.schedule.clinicalrecord.adapter.out.persistence;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Repository;

import com.clinic.schedule.clinicalrecord.application.exception.ConsultationChangedException;
import com.clinic.schedule.clinicalrecord.application.port.ConsultationRepository;
import com.clinic.schedule.clinicalrecord.domain.Consultation;
import com.clinic.schedule.clinicalrecord.domain.ConsultationId;
import com.clinic.schedule.clinicalrecord.domain.PatientId;
import com.clinic.schedule.clinicalrecord.domain.PractitionerId;

/** Implements the port over JPA. The only place that knows consultations live in tables. */
@Repository
class ConsultationJpaAdapter implements ConsultationRepository {

    private final ConsultationRowRepository rows;

    ConsultationJpaAdapter(ConsultationRowRepository rows) {
        this.rows = rows;
    }

    @Override
    public Optional<Consultation> findById(ConsultationId id) {
        return rows.findById(id.value()).map(ConsultationRow::toConsultation);
    }

    @Override
    public List<Consultation> findAllForPatient(PatientId patientId) {
        return rows.findByPatientId(patientId.value()).stream().map(ConsultationRow::toConsultation).toList();
    }

    @Override
    public Optional<LocalDateTime> findLastRecordedAt(PatientId patientId, PractitionerId practitionerId) {
        return rows.findLastRecordedAt(patientId.value(), practitionerId.value());
    }

    /**
     * A new consultation is inserted whole. An existing one only ever gains addenda (D-47, D-52):
     * they are added to the row read earlier in the same transaction, whose version then says
     * whether someone else added one meanwhile. It flushes at once, so that refusal is answered
     * here, as a {@link ConsultationChangedException}, and not at commit.
     */
    @Override
    public void save(Consultation consultation) {
        try {
            ConsultationRow row = rows.findById(consultation.id().value())
                    .map(existing -> {
                        existing.addAddendaFrom(consultation);
                        return existing;
                    })
                    .orElseGet(() -> ConsultationRow.from(consultation));
            rows.saveAndFlush(row);
        } catch (ObjectOptimisticLockingFailureException e) {
            throw new ConsultationChangedException(consultation.id());
        }
    }
}
