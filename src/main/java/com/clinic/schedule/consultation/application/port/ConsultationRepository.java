package com.clinic.schedule.consultation.application.port;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import com.clinic.schedule.consultation.domain.Consultation;
import com.clinic.schedule.consultation.domain.ConsultationId;
import com.clinic.schedule.consultation.domain.PatientId;
import com.clinic.schedule.consultation.domain.PractitionerId;

/** Where consultations are kept. They are written once and read, never changed (D-47). */
public interface ConsultationRepository {

    Optional<Consultation> findById(ConsultationId id);

    /** Every consultation of the patient, from every practitioner: their clinical record. */
    List<Consultation> findAllForPatient(PatientId patientId);

    /** When that practitioner last recorded a consultation of that patient, if ever (D-49). */
    Optional<LocalDateTime> findLastRecordedAt(PatientId patientId, PractitionerId practitionerId);

    void save(Consultation consultation);
}
