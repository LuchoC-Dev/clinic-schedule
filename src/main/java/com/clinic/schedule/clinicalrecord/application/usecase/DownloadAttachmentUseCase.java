package com.clinic.schedule.clinicalrecord.application.usecase;

import java.time.Clock;
import java.time.LocalDate;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.clinic.schedule.clinicalrecord.application.exception.ClinicalRecordAccessDeniedException;
import com.clinic.schedule.clinicalrecord.application.port.AttachmentContentStore;
import com.clinic.schedule.clinicalrecord.application.port.AttachmentRepository;
import com.clinic.schedule.clinicalrecord.application.port.CurrentPractitionerPort;
import com.clinic.schedule.clinicalrecord.domain.Attachment;
import com.clinic.schedule.clinicalrecord.domain.AttachmentId;
import com.clinic.schedule.clinicalrecord.domain.PractitionerId;

/**
 * A file attached to a record, for whoever may read that record, and nobody else: files are never
 * served at a public address (D-57). An attachment that does not exist is refused the same way.
 */
@Service
public class DownloadAttachmentUseCase {

    private final AttachmentRepository attachments;
    private final AttachmentContentStore contents;
    private final CurrentPractitionerPort currentPractitioner;
    private final ClinicalRecordAccessReader access;
    private final Clock clock;

    public DownloadAttachmentUseCase(AttachmentRepository attachments, AttachmentContentStore contents,
            CurrentPractitionerPort currentPractitioner, ClinicalRecordAccessReader access, Clock clock) {
        this.attachments = attachments;
        this.contents = contents;
        this.currentPractitioner = currentPractitioner;
        this.access = access;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public Download handle(AttachmentId id) {
        PractitionerId practitionerId = currentPractitioner.practitionerId()
                .orElseThrow(ClinicalRecordAccessDeniedException::toRead);
        Attachment attachment = attachments.findById(id).orElseThrow(ClinicalRecordAccessDeniedException::toRead);
        if (!access.mayRead(practitionerId, attachment.patientId(), LocalDate.now(clock))) {
            throw ClinicalRecordAccessDeniedException.toRead();
        }
        byte[] content = contents.get(id)
                .orElseThrow(() -> new IllegalStateException("The content of attachment " + id.value() + " is missing"));
        return new Download(attachment, content);
    }

    /** What describes the file, and the file. */
    public record Download(Attachment attachment, byte[] content) {
    }
}
