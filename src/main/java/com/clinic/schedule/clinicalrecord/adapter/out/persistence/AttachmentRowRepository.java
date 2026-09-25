package com.clinic.schedule.clinicalrecord.adapter.out.persistence;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

/** Spring Data over {@link AttachmentRow}; the application talks to {@code AttachmentRepository}, the port. */
interface AttachmentRowRepository extends JpaRepository<AttachmentRow, UUID> {

    List<AttachmentRow> findByPatientId(UUID patientId);

    boolean existsByConsultationId(UUID consultationId);

    boolean existsByCheckId(UUID checkId);
}
