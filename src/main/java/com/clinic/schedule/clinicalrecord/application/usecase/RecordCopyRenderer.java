package com.clinic.schedule.clinicalrecord.application.usecase;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.clinic.schedule.clinicalrecord.application.port.AttachmentContentStore;
import com.clinic.schedule.clinicalrecord.application.port.AttachmentRepository;
import com.clinic.schedule.clinicalrecord.application.port.CheckOrderRepository;
import com.clinic.schedule.clinicalrecord.application.port.CheckRepository;
import com.clinic.schedule.clinicalrecord.application.port.ConsultationRepository;
import com.clinic.schedule.clinicalrecord.application.port.RecordCopyDocumentPort;
import com.clinic.schedule.clinicalrecord.domain.Attachment;
import com.clinic.schedule.clinicalrecord.domain.AttachmentId;
import com.clinic.schedule.clinicalrecord.domain.PatientId;
import com.clinic.schedule.clinicalrecord.domain.RecordCopy;
import com.clinic.schedule.clinicalrecord.domain.RecordCopyContent;

/**
 * Puts a copy of a record together again from the record, as it stood when the copy was assembled, and turns it
 * into its document (D-95). Every use case that hands a copy to someone asks the same, so it is read in one
 * place. Package-private: a detail of the use cases, which own the transaction and record the reading.
 */
@Component
class RecordCopyRenderer {

    private final ConsultationRepository consultations;
    private final CheckRepository checks;
    private final CheckOrderRepository orders;
    private final AttachmentRepository attachments;
    private final AttachmentContentStore contents;
    private final RecordCopyDocumentPort documents;

    RecordCopyRenderer(ConsultationRepository consultations, CheckRepository checks, CheckOrderRepository orders,
            AttachmentRepository attachments, AttachmentContentStore contents, RecordCopyDocumentPort documents) {
        this.consultations = consultations;
        this.checks = checks;
        this.orders = orders;
        this.attachments = attachments;
        this.contents = contents;
        this.documents = documents;
    }

    byte[] render(RecordCopy copy) {
        PatientId patientId = copy.patientId();
        RecordCopyContent content = RecordCopyContent.asOf(copy.assembledAt(), patientId,
                consultations.findAllForPatient(patientId), checks.findAllForPatient(patientId),
                orders.findAllForPatient(patientId), attachments.findAllForPatient(patientId));
        Map<AttachmentId, byte[]> files = new LinkedHashMap<>();
        for (Attachment attachment : content.attachments()) {
            files.put(attachment.id(), contents.get(attachment.id()).orElseThrow(() -> new IllegalStateException(
                    "The content of attachment " + attachment.id().value() + " is missing")));
        }
        return documents.render(copy, content, files);
    }
}
