package com.clinic.schedule.clinicalrecord.application.usecase;

import java.time.Clock;
import java.time.LocalDateTime;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.clinic.schedule.clinicalrecord.application.exception.ClinicalRecordAccessDeniedException;
import com.clinic.schedule.clinicalrecord.application.port.AttachmentContentStore;
import com.clinic.schedule.clinicalrecord.application.port.AttachmentRepository;
import com.clinic.schedule.clinicalrecord.application.port.CurrentPractitionerPort;
import com.clinic.schedule.clinicalrecord.application.port.RecordReadingWriter;
import com.clinic.schedule.clinicalrecord.domain.Attachment;
import com.clinic.schedule.clinicalrecord.domain.AttachmentId;
import com.clinic.schedule.clinicalrecord.domain.PractitionerId;
import com.clinic.schedule.clinicalrecord.domain.ReadAccess;
import com.clinic.schedule.clinicalrecord.domain.RecordReading;

/**
 * A file attached to a record, for whoever may read that record, and nobody else: files are never
 * served at a public address (D-57). An attachment that does not exist is refused the same way.
 * Downloading it is reading the record, and is recorded as such, with the file (D-59).
 */
@Service
public class DownloadAttachmentUseCase {

    private final AttachmentRepository attachments;
    private final AttachmentContentStore contents;
    private final RecordReadingWriter readings;
    private final CurrentPractitionerPort currentPractitioner;
    private final ClinicalRecordAccessReader access;
    private final Clock clock;

    public DownloadAttachmentUseCase(AttachmentRepository attachments, AttachmentContentStore contents,
            RecordReadingWriter readings, CurrentPractitionerPort currentPractitioner,
            ClinicalRecordAccessReader access, Clock clock) {
        this.attachments = attachments;
        this.contents = contents;
        this.readings = readings;
        this.currentPractitioner = currentPractitioner;
        this.access = access;
        this.clock = clock;
    }

    @Transactional
    public Download handle(AttachmentId id) {
        PractitionerId practitionerId = currentPractitioner.practitionerId()
                .orElseThrow(ClinicalRecordAccessDeniedException::toRead);
        Attachment attachment = attachments.findById(id).orElseThrow(ClinicalRecordAccessDeniedException::toRead);
        LocalDateTime now = LocalDateTime.now(clock);
        ReadAccess readAccess = access.readAccess(practitionerId, attachment.patientId(), now.toLocalDate())
                .orElseThrow(ClinicalRecordAccessDeniedException::toRead);
        byte[] content = contents.get(id)
                .orElseThrow(() -> new IllegalStateException("The content of attachment " + id.value() + " is missing"));
        readings.save(RecordReading.ofAttachment(practitionerId, attachment.patientId(), now, readAccess, id));
        return new Download(attachment, content);
    }

    /** What describes the file, and the file. */
    public record Download(Attachment attachment, byte[] content) {
    }
}
