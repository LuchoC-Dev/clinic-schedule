package com.clinic.schedule.clinicalrecord.adapter.out.persistence;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.clinic.schedule.clinicalrecord.domain.CopyDelivery;

/** Spring Data over {@link RecordCopyRow}; the application talks to {@code RecordCopyRepository}, the port. */
interface RecordCopyRowRepository extends JpaRepository<RecordCopyRow, UUID> {

    Optional<RecordCopyRow> findByCode(String code);

    List<RecordCopyRow> findByAuthenticatedAtIsNull();

    List<RecordCopyRow> findByDeliveryAndAuthenticatedAtIsNotNullAndHandedOverAtIsNull(CopyDelivery delivery);

    List<RecordCopyRow> findByPatientIdIn(Collection<UUID> patientIds);

    boolean existsByPatientIdAndAuthenticatedAtIsNull(UUID patientId);
}
