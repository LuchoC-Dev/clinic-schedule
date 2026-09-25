package com.clinic.schedule.clinicalrecord.application.usecase;

import java.time.Clock;
import java.time.LocalDateTime;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.clinic.schedule.clinicalrecord.application.exception.ClinicalRecordAccessDeniedException;
import com.clinic.schedule.clinicalrecord.application.port.AttachmentContentStore;
import com.clinic.schedule.clinicalrecord.application.port.AttachmentRepository;
import com.clinic.schedule.clinicalrecord.application.port.CheckRepository;
import com.clinic.schedule.clinicalrecord.application.port.ConsultationRepository;
import com.clinic.schedule.clinicalrecord.application.port.CurrentPractitionerPort;
import com.clinic.schedule.clinicalrecord.domain.Attachment;
import com.clinic.schedule.clinicalrecord.domain.AttachmentId;
import com.clinic.schedule.clinicalrecord.domain.AttachmentOwner;
import com.clinic.schedule.clinicalrecord.domain.Check;
import com.clinic.schedule.clinicalrecord.domain.Consultation;
import com.clinic.schedule.clinicalrecord.domain.PatientId;
import com.clinic.schedule.clinicalrecord.domain.PractitionerId;

/**
 * A practitioner attaches a file to a consultation or a check, at any time, like an addendum: any
 * practitioner who may read the patient's record, with the practice's clock (D-57). What it belongs
 * to must exist; one that does not is refused like a record the user may not read.
 *
 * <p>What describes the file and the file itself are stored in the same transaction while the
 * files live in the database. An object store will not join it, and will have to write the file
 * first and clear what is left behind by a failed write — decided when that store arrives.
 */
@Service
public class AddAttachmentUseCase {

    private final AttachmentRepository attachments;
    private final AttachmentContentStore contents;
    private final ConsultationRepository consultations;
    private final CheckRepository checks;
    private final CurrentPractitionerPort currentPractitioner;
    private final ClinicalRecordAccessReader access;
    private final Clock clock;

    public AddAttachmentUseCase(AttachmentRepository attachments, AttachmentContentStore contents,
            ConsultationRepository consultations, CheckRepository checks, CurrentPractitionerPort currentPractitioner,
            ClinicalRecordAccessReader access, Clock clock) {
        this.attachments = attachments;
        this.contents = contents;
        this.consultations = consultations;
        this.checks = checks;
        this.currentPractitioner = currentPractitioner;
        this.access = access;
        this.clock = clock;
    }

    /**
     * @throws com.clinic.schedule.clinicalrecord.domain.exception.UnsupportedFileException when it
     *         is not a PDF, a PNG or a JPEG
     * @throws com.clinic.schedule.clinicalrecord.domain.exception.FileTooLargeException when it is
     *         too large
     */
    @Transactional
    public Attachment handle(Command command) {
        PractitionerId practitionerId = currentPractitioner.practitionerId()
                .orElseThrow(ClinicalRecordAccessDeniedException::toRead);
        PatientId patientId = patientOf(command.owner());
        LocalDateTime now = LocalDateTime.now(clock);
        if (!access.mayRead(practitionerId, patientId, now.toLocalDate())) {
            throw ClinicalRecordAccessDeniedException.toRead();
        }

        Attachment attachment = Attachment.upload(AttachmentId.newId(), patientId, command.owner(), practitionerId, now,
                command.fileName(), command.content());
        attachments.save(attachment);
        contents.put(attachment.id(), command.content());
        return attachment;
    }

    private PatientId patientOf(AttachmentOwner owner) {
        return switch (owner) {
            case AttachmentOwner.OfConsultation of -> consultations.findById(of.consultation())
                    .map(Consultation::patientId)
                    .orElseThrow(ClinicalRecordAccessDeniedException::toRead);
            case AttachmentOwner.OfCheck of -> checks.findById(of.check())
                    .map(Check::patientId)
                    .orElseThrow(ClinicalRecordAccessDeniedException::toRead);
        };
    }

    public record Command(AttachmentOwner owner, String fileName, byte[] content) {
    }
}
