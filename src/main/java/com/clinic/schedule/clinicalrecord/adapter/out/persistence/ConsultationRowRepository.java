package com.clinic.schedule.clinicalrecord.adapter.out.persistence;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

/**
 * Spring Data over {@link ConsultationRow}. A detail of this package: the application talks to
 * {@code ConsultationRepository}, the port, and never to this interface.
 */
interface ConsultationRowRepository extends JpaRepository<ConsultationRow, UUID> {

    List<ConsultationRow> findByPatientId(UUID patientId);

    @Query("select max(c.recordedAt) from ConsultationRow c where c.patientId = :patientId and c.practitionerId = :practitionerId")
    Optional<LocalDateTime> findLastRecordedAt(UUID patientId, UUID practitionerId);
}
