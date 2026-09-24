package com.clinic.schedule.consultation.adapter.out.persistence;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Repository;

import com.clinic.schedule.consultation.application.port.ConsultationRepository;
import com.clinic.schedule.consultation.domain.Consultation;
import com.clinic.schedule.consultation.domain.ConsultationId;
import com.clinic.schedule.consultation.domain.PatientId;
import com.clinic.schedule.consultation.domain.PractitionerId;

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

    /** Only ever a new consultation: nothing changes one once it is sent (D-47). */
    @Override
    public void save(Consultation consultation) {
        rows.save(ConsultationRow.from(consultation));
    }
}
