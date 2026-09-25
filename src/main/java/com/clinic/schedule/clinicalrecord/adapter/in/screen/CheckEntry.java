package com.clinic.schedule.clinicalrecord.adapter.in.screen;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.clinic.schedule.clinicalrecord.domain.Attachment;
import com.clinic.schedule.clinicalrecord.domain.AttachmentOwner;
import com.clinic.schedule.clinicalrecord.domain.Check;

/**
 * One check of a clinical record screen: what was done, when and by whom, and its result with its
 * author — or that it is pending, when {@code pending} is true and the result fields are null.
 */
record CheckEntry(UUID id, String performedAt, String performedBy, String description, boolean pending,
        String result, String resultAuthor, String resultWrittenAt, List<AttachmentEntry> attachments) {

    /**
     * @param attachments every attachment of the record; this check's are picked
     * @param names every practitioner's name by id
     */
    static CheckEntry of(Check check, List<Attachment> attachments, Map<UUID, String> names) {
        return new CheckEntry(
                check.id().value(),
                ClinicalRecordEntry.format(check.performedAt()),
                ClinicalRecordEntry.nameOf(names, check.performedBy().value()),
                check.description(),
                check.isPending(),
                check.result().map(result -> result.text()).orElse(null),
                check.result().map(result -> ClinicalRecordEntry.nameOf(names, result.author().value())).orElse(null),
                check.result().map(result -> ClinicalRecordEntry.format(result.writtenAt())).orElse(null),
                AttachmentEntry.ofOwner(attachments, new AttachmentOwner.OfCheck(check.id()), names));
    }
}
