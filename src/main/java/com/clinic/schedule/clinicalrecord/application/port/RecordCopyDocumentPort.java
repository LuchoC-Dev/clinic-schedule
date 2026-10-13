package com.clinic.schedule.clinicalrecord.application.port;

import java.util.Map;

import com.clinic.schedule.clinicalrecord.domain.AttachmentId;
import com.clinic.schedule.clinicalrecord.domain.RecordCopy;
import com.clinic.schedule.clinicalrecord.domain.RecordCopyContent;

/**
 * Turns a copy of a record into the document handed to the patient — a PDF today — with every entry, its author
 * and when it was written, the code on each page, and the files attached (D-95). It reads nothing of the record:
 * it is given it.
 */
public interface RecordCopyDocumentPort {

    /** @param files the content of every file the copy holds, by attachment */
    byte[] render(RecordCopy copy, RecordCopyContent content, Map<AttachmentId, byte[]> files);
}
