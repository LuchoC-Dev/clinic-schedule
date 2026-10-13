package com.clinic.schedule.clinicalrecord.domain;

import java.time.LocalDateTime;
import java.util.Optional;

/**
 * A practitioner read a patient's clinical record (D-59): who — the practitioner, and the user they
 * logged in as —, whose, when, by which access, and — when what they read was a file attached to it
 * — which one. Every reading leaves one, so no clinical content reaches anyone without a trace.
 *
 * <p>A copy of the record is read too (D-96): by a practitioner reviewing it, with their access; or by
 * whoever asked for it, with no practitioner — the account that downloaded it, or the user of reception
 * who handed the envelope over —, by {@link ReadAccess#COPY_REQUEST}. Either names the copy.
 *
 * <p>Plain domain object and immutable. The admins read them, never their content (D-71).
 */
public class RecordReading {

    private final RecordReadingId id;
    private final PractitionerId practitionerId;
    private final String readBy;
    private final PatientId patientId;
    private final LocalDateTime readAt;
    private final ReadAccess access;
    private final AttachmentId attachmentId;
    private final RecordCopyId recordCopyId;

    private RecordReading(RecordReadingId id, PractitionerId practitionerId, String readBy, PatientId patientId,
            LocalDateTime readAt, ReadAccess access, AttachmentId attachmentId, RecordCopyId recordCopyId) {
        if (id == null || patientId == null || readAt == null || access == null) {
            throw new IllegalArgumentException("A reading of a record needs an id, a patient, its time and its access");
        }
        if (access == ReadAccess.COPY_REQUEST ? practitionerId != null || recordCopyId == null : practitionerId == null) {
            throw new IllegalArgumentException(
                    "A practitioner reads a record by their access; a copy is handed over by the patient's right alone");
        }
        this.id = id;
        this.practitionerId = practitionerId;
        this.readBy = readBy;
        this.patientId = patientId;
        this.readAt = readAt;
        this.access = access;
        this.attachmentId = attachmentId;
        this.recordCopyId = recordCopyId;
    }

    /** The record was read whole, by that practitioner logged in as {@code readBy}. */
    public static RecordReading ofRecord(PractitionerId practitionerId, String readBy, PatientId patientId,
            LocalDateTime readAt, ReadAccess access) {
        return new RecordReading(RecordReadingId.newId(), practitionerId, requireUser(readBy), patientId, readAt,
                access, null, null);
    }

    /** A file attached to the record was read, by that practitioner logged in as {@code readBy}. */
    public static RecordReading ofAttachment(PractitionerId practitionerId, String readBy, PatientId patientId,
            LocalDateTime readAt, ReadAccess access, AttachmentId attachmentId) {
        if (attachmentId == null) {
            throw new IllegalArgumentException("A reading of an attachment needs the attachment");
        }
        return new RecordReading(RecordReadingId.newId(), practitionerId, requireUser(readBy), patientId, readAt,
                access, attachmentId, null);
    }

    /** A copy of the record was read by a practitioner — reviewing it, or printing it —, by their access (D-96). */
    public static RecordReading ofCopy(PractitionerId practitionerId, String readBy, PatientId patientId,
            LocalDateTime readAt, ReadAccess access, RecordCopyId recordCopyId) {
        if (recordCopyId == null) {
            throw new IllegalArgumentException("A reading of a copy needs the copy");
        }
        return new RecordReading(RecordReadingId.newId(), practitionerId, requireUser(readBy), patientId, readAt,
                access, null, recordCopyId);
    }

    /**
     * A copy of the record was handed to whoever asked for it (D-96): downloaded by the account that goes by
     * {@code readBy}, or handed over at reception by that user.
     */
    public static RecordReading ofDeliveredCopy(String readBy, PatientId patientId, LocalDateTime readAt,
            RecordCopyId recordCopyId) {
        return new RecordReading(RecordReadingId.newId(), null, requireUser(readBy), patientId, readAt,
                ReadAccess.COPY_REQUEST, null, recordCopyId);
    }

    /**
     * A reading as it was recorded, for reading it back. The user is null for the readings recorded
     * before users were (D-71).
     */
    public static RecordReading restore(RecordReadingId id, PractitionerId practitionerId, String readBy,
            PatientId patientId, LocalDateTime readAt, ReadAccess access, AttachmentId attachmentId) {
        return restore(id, practitionerId, readBy, patientId, readAt, access, attachmentId, null);
    }

    /** A reading as it was recorded, a copy's included (D-96). */
    public static RecordReading restore(RecordReadingId id, PractitionerId practitionerId, String readBy,
            PatientId patientId, LocalDateTime readAt, ReadAccess access, AttachmentId attachmentId,
            RecordCopyId recordCopyId) {
        return new RecordReading(id, practitionerId, readBy, patientId, readAt, access, attachmentId, recordCopyId);
    }

    private static String requireUser(String readBy) {
        if (readBy == null || readBy.isBlank()) {
            throw new IllegalArgumentException("A reading of a record needs the user who read it");
        }
        return readBy;
    }

    public RecordReadingId id() {
        return id;
    }

    /** Empty for a copy handed to whoever asked for it, which no practitioner read (D-96). */
    public Optional<PractitionerId> practitionerId() {
        return Optional.ofNullable(practitionerId);
    }

    /** The username the practitioner read it as; empty for a reading recorded before users were. */
    public Optional<String> readBy() {
        return Optional.ofNullable(readBy);
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

    /** Empty unless what was read was a copy of the record (D-96). */
    public Optional<RecordCopyId> recordCopyId() {
        return Optional.ofNullable(recordCopyId);
    }
}
