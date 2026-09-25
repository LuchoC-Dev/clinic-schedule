package com.clinic.schedule.clinicalrecord.adapter.out.persistence;

import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Repository;

import com.clinic.schedule.clinicalrecord.application.port.AttachmentRepository;
import com.clinic.schedule.clinicalrecord.domain.Attachment;
import com.clinic.schedule.clinicalrecord.domain.AttachmentId;
import com.clinic.schedule.clinicalrecord.domain.AttachmentOwner;
import com.clinic.schedule.clinicalrecord.domain.PatientId;

/** Implements the port over JPA: what describes each attachment, never its content. */
@Repository
class AttachmentJpaAdapter implements AttachmentRepository {

    private final AttachmentRowRepository rows;

    AttachmentJpaAdapter(AttachmentRowRepository rows) {
        this.rows = rows;
    }

    @Override
    public Optional<Attachment> findById(AttachmentId id) {
        return rows.findById(id.value()).map(AttachmentRow::toAttachment);
    }

    @Override
    public List<Attachment> findAllForPatient(PatientId patientId) {
        return rows.findByPatientId(patientId.value()).stream().map(AttachmentRow::toAttachment).toList();
    }

    @Override
    public boolean existsFor(AttachmentOwner owner) {
        return switch (owner) {
            case AttachmentOwner.OfConsultation of -> rows.existsByConsultationId(of.consultation().value());
            case AttachmentOwner.OfCheck of -> rows.existsByCheckId(of.check().value());
        };
    }

    /** Only ever a new attachment: nothing changes one once it is uploaded (D-57). */
    @Override
    public void save(Attachment attachment) {
        rows.save(AttachmentRow.from(attachment));
    }
}
