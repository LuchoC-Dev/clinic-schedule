package com.clinic.schedule.clinicalrecord.adapter.in.http;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

import com.clinic.schedule.clinicalrecord.domain.Attachment;
import com.clinic.schedule.clinicalrecord.domain.AttachmentOwner;

/**
 * What describes an attached file, as the API shows it: what it belongs to — a consultation or a
 * check, the other left null — what it is, who uploaded it and when. The file itself is downloaded
 * apart, from {@code /api/attachments/{id}} (D-57).
 */
record AttachmentResponse(
        UUID id,
        UUID consultationId,
        UUID checkId,
        String fileName,
        String mediaType,
        long sizeBytes,
        String sha256,
        UUID uploadedBy,
        String uploadedByName,
        LocalDateTime uploadedAt) {

    /** @param names every practitioner's name by id; an unknown one is left null */
    static AttachmentResponse of(Attachment attachment, Map<UUID, String> names) {
        return new AttachmentResponse(
                attachment.id().value(),
                attachment.owner() instanceof AttachmentOwner.OfConsultation of ? of.consultation().value() : null,
                attachment.owner() instanceof AttachmentOwner.OfCheck of ? of.check().value() : null,
                attachment.fileName(),
                attachment.kind().mediaType(),
                attachment.sizeBytes(),
                attachment.sha256(),
                attachment.uploadedBy().value(),
                names.get(attachment.uploadedBy().value()),
                attachment.uploadedAt());
    }
}
