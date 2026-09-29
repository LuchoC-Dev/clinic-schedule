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
import com.clinic.schedule.clinicalrecord.application.port.RecordRefusalWriter;
import com.clinic.schedule.clinicalrecord.domain.Attachment;
import com.clinic.schedule.clinicalrecord.domain.AttachmentId;
import com.clinic.schedule.clinicalrecord.domain.PatientId;
import com.clinic.schedule.clinicalrecord.domain.PractitionerId;
import com.clinic.schedule.clinicalrecord.domain.ReadAccess;
import com.clinic.schedule.clinicalrecord.domain.RecordReading;
import com.clinic.schedule.clinicalrecord.domain.RecordRefusal;

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
    private final RecordRefusalWriter refusals;
    private final CurrentPractitionerPort currentPractitioner;
    private final ClinicalRecordAccessReader access;
    private final Clock clock;

    public DownloadAttachmentUseCase(AttachmentRepository attachments, AttachmentContentStore contents,
            RecordReadingWriter readings, RecordRefusalWriter refusals, CurrentPractitionerPort currentPractitioner,
            ClinicalRecordAccessReader access, Clock clock) {
        this.attachments = attachments;
        this.contents = contents;
        this.readings = readings;
        this.refusals = refusals;
        this.currentPractitioner = currentPractitioner;
        this.access = access;
        this.clock = clock;
    }

    @Transactional
    public Download handle(AttachmentId id) {
        LocalDateTime now = LocalDateTime.now(clock);
        PractitionerId practitionerId = currentPractitioner.practitionerId()
                .orElseThrow(() -> refused(null, id, null, now));
        Attachment attachment = attachments.findById(id).orElseThrow(() -> refused(practitionerId, id, null, now));
        ReadAccess readAccess = access.readAccess(practitionerId, attachment.patientId(), now.toLocalDate())
                .orElseThrow(() -> refused(practitionerId, id, attachment.patientId(), now));
        byte[] content = contents.get(id)
                .orElseThrow(() -> new IllegalStateException("The content of attachment " + id.value() + " is missing"));
        readings.save(RecordReading.ofAttachment(practitionerId, currentPractitioner.username(), attachment.patientId(),
                now, readAccess, id));
        return new Download(attachment, content);
    }

    /** What describes the file, and the file. */
    public record Download(Attachment attachment, byte[] content) {
    }

    /** Records the refusal, and gives the 403 to throw. The patient is null for a file that does not exist. */
    private ClinicalRecordAccessDeniedException refused(PractitionerId practitionerId, AttachmentId id,
            PatientId patientId, LocalDateTime at) {
        refusals.save(RecordRefusal.ofAttachment(currentPractitioner.username(), practitionerId, id, patientId, at));
        return ClinicalRecordAccessDeniedException.toRead();
    }
}
