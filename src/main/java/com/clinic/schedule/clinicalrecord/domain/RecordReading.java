package com.clinic.schedule.clinicalrecord.domain;

import java.time.LocalDateTime;
import java.util.Optional;

/**
 * A practitioner read a patient's clinical record (D-59): who, whose, when, by which access, and —
 * when what they read was a file attached to it — which one. Every reading leaves one, so no
 * clinical content reaches anyone without a trace.
 *
 * <p>Plain domain object and immutable. It is only ever written: nobody reads the readings yet,
 * and admins will.
 */
public class RecordReading {

    private final RecordReadingId id;
    private final PractitionerId practitionerId;
    private final PatientId patientId;
    private final LocalDateTime readAt;
    private final ReadAccess access;
    private final AttachmentId attachmentId;

    private RecordReading(RecordReadingId id, PractitionerId practitionerId, PatientId patientId,
            LocalDateTime readAt, ReadAccess access, AttachmentId attachmentId) {
        if (id == null || practitionerId == null || patientId == null || readAt == null || access == null) {
            throw new IllegalArgumentException(
                    "A reading of a record needs an id, a practitioner, a patient, its time and its access");
        }
        this.id = id;
        this.practitionerId = practitionerId;
        this.patientId = patientId;
        this.readAt = readAt;
        this.access = access;
        this.attachmentId = attachmentId;
    }

    /** The record was read whole. */
    public static RecordReading ofRecord(PractitionerId practitionerId, PatientId patientId, LocalDateTime readAt,
            ReadAccess access) {
        return new RecordReading(RecordReadingId.newId(), practitionerId, patientId, readAt, access, null);
    }

    /** A file attached to the record was read. */
    public static RecordReading ofAttachment(PractitionerId practitionerId, PatientId patientId,
            LocalDateTime readAt, ReadAccess access, AttachmentId attachmentId) {
        if (attachmentId == null) {
            throw new IllegalArgumentException("A reading of an attachment needs the attachment");
        }
        return new RecordReading(RecordReadingId.newId(), practitionerId, patientId, readAt, access, attachmentId);
    }

    public RecordReadingId id() {
        return id;
    }

    public PractitionerId practitionerId() {
        return practitionerId;
    }

    public PatientId patientId() {
        return patientId;
    }

    public LocalDateTime readAt() {
        return readAt;
    }

    public ReadAccess access() {
        return access;
    }

    /** Empty when the record was read whole. */
    public Optional<AttachmentId> attachmentId() {
        return Optional.ofNullable(attachmentId);
    }
}
