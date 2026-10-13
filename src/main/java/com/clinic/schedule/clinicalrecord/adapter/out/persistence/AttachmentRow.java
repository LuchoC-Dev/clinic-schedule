package com.clinic.schedule.clinicalrecord.adapter.out.persistence;

import java.time.LocalDateTime;
import java.util.UUID;

import com.clinic.schedule.clinicalrecord.domain.Attachment;
import com.clinic.schedule.clinicalrecord.domain.AttachmentId;
import com.clinic.schedule.clinicalrecord.domain.AttachmentOwner;
import com.clinic.schedule.clinicalrecord.domain.CheckId;
import com.clinic.schedule.clinicalrecord.domain.ConsultationId;
import com.clinic.schedule.clinicalrecord.domain.FileKind;
import com.clinic.schedule.clinicalrecord.domain.PatientId;
import com.clinic.schedule.clinicalrecord.domain.PractitionerId;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * What describes an attachment, as a row of {@code attachment}; its content is a row of another
 * table, so listing a record's attachments never reads the files (D-57). The owner is one of two
 * columns, the other empty; both empty, the file is of the record as a whole (D-94).
 */
@Entity
@Table(name = "attachment")
class AttachmentRow {

    @Id
    private UUID id;

    @Column(name = "patient_id", nullable = false)
    private UUID patientId;

    @Column(name = "consultation_id")
    private UUID consultationId;

    @Column(name = "check_id")
    private UUID checkId;

    @Column(name = "uploaded_by", nullable = false)
    private UUID uploadedBy;

    @Column(name = "uploaded_at", nullable = false)
    private LocalDateTime uploadedAt;

    @Column(name = "file_name", nullable = false)
    private String fileName;

    @Column(name = "media_type", nullable = false)
    private String mediaType;

    @Column(name = "size_bytes", nullable = false)
    private long sizeBytes;

    @Column(nullable = false)
    private String sha256;

    protected AttachmentRow() {
        // required by JPA
    }

    static AttachmentRow from(Attachment attachment) {
        AttachmentRow row = new AttachmentRow();
        row.id = attachment.id().value();
        row.patientId = attachment.patientId().value();
        switch (attachment.owner()) {
            case AttachmentOwner.OfConsultation of -> row.consultationId = of.consultation().value();
            case AttachmentOwner.OfCheck of -> row.checkId = of.check().value();
            case AttachmentOwner.OfRecord of -> {
                // neither column: the patient's is already kept
            }
        }
        row.uploadedBy = attachment.uploadedBy().value();
        row.uploadedAt = attachment.uploadedAt();
        row.fileName = attachment.fileName();
        row.mediaType = attachment.kind().mediaType();
        row.sizeBytes = attachment.sizeBytes();
        row.sha256 = attachment.sha256();
        return row;
    }

    Attachment toAttachment() {
        AttachmentOwner owner;
        if (consultationId != null) {
            owner = new AttachmentOwner.OfConsultation(new ConsultationId(consultationId));
        } else if (checkId != null) {
            owner = new AttachmentOwner.OfCheck(new CheckId(checkId));
        } else {
            owner = new AttachmentOwner.OfRecord(new PatientId(patientId));
        }
        return Attachment.restore(new AttachmentId(id), new PatientId(patientId), owner, new PractitionerId(uploadedBy),
                uploadedAt, fileName, FileKind.withMediaType(mediaType), sizeBytes, sha256);
    }
}
