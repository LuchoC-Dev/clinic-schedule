package com.clinic.schedule.clinicalrecord.adapter.out.persistence;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

/** Spring Data over {@link CheckRow}; the application talks to {@code CheckRepository}, the port. */
interface CheckRowRepository extends JpaRepository<CheckRow, UUID> {

    List<CheckRow> findByPatientId(UUID patientId);

    List<CheckRow> findByConsultationIdOrderByPerformedAt(UUID consultationId);

    @Query("select max(c.performedAt) from CheckRow c where c.patientId = :patientId and c.performedBy = :practitionerId")
    Optional<LocalDateTime> findLastPerformedAt(UUID patientId, UUID practitionerId);
}
