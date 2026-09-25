package com.clinic.schedule.clinicalrecord.adapter.in.screen;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import com.clinic.schedule.clinicalrecord.domain.Attachment;
import com.clinic.schedule.clinicalrecord.domain.AttachmentOwner;

/** One attached file of a clinical record screen: its name, size, who uploaded it and when (D-57). */
record AttachmentEntry(UUID id, String fileName, String size, String uploadedBy, String uploadedAt) {

    static AttachmentEntry of(Attachment attachment, Map<UUID, String> names) {
        return new AttachmentEntry(attachment.id().value(), attachment.fileName(), sizeOf(attachment.sizeBytes()),
                ClinicalRecordEntry.nameOf(names, attachment.uploadedBy().value()),
                ClinicalRecordEntry.format(attachment.uploadedAt()));
    }

    /** The attachments that belong to that owner, in the order they were uploaded. */
    static List<AttachmentEntry> ofOwner(List<Attachment> attachments, AttachmentOwner owner, Map<UUID, String> names) {
        return attachments.stream()
                .filter(attachment -> attachment.owner().equals(owner))
                .map(attachment -> of(attachment, names))
                .toList();
    }

    private static String sizeOf(long bytes) {
        return bytes < 1024 * 1024
                ? Math.max(1, bytes / 1024) + " KB"
                : String.format(Locale.forLanguageTag("es-AR"), "%.1f MB", bytes / (1024.0 * 1024));
    }
}
