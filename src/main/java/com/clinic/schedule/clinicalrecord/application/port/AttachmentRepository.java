package com.clinic.schedule.clinicalrecord.application.port;

import java.util.List;
import java.util.Optional;

import com.clinic.schedule.clinicalrecord.domain.Attachment;
import com.clinic.schedule.clinicalrecord.domain.AttachmentId;
import com.clinic.schedule.clinicalrecord.domain.AttachmentOwner;
import com.clinic.schedule.clinicalrecord.domain.PatientId;

/**
 * Where what describes each attachment is kept — never its content, which {@link AttachmentContentStore}
 * keeps (D-57). Attachments are only added.
 */
public interface AttachmentRepository {

    Optional<Attachment> findById(AttachmentId id);

    /** Every attachment of the patient's record, of consultations and checks alike. */
    List<Attachment> findAllForPatient(PatientId patientId);

    /** Whether anything is attached to that consultation or check. */
    boolean existsFor(AttachmentOwner owner);

    void save(Attachment attachment);
}
