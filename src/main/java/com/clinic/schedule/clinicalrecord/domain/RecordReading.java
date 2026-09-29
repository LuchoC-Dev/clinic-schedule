package com.clinic.schedule.clinicalrecord.domain;

import java.time.LocalDateTime;
import java.util.Optional;

/**
 * A practitioner read a patient's clinical record (D-59): who — the practitioner, and the user they
 * logged in as —, whose, when, by which access, and — when what they read was a file attached to it
 * — which one. Every reading leaves one, so no clinical content reaches anyone without a trace.
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

    private RecordReading(RecordReadingId id, PractitionerId practitionerId, String readBy, PatientId patientId,
            LocalDateTime readAt, ReadAccess access, AttachmentId attachmentId) {
        if (id == null || practitionerId == null || patientId == null || readAt == null || access == null) {
            throw new IllegalArgumentException(
                    "A reading of a record needs an id, a practitioner, a patient, its time and its access");
        }
        this.id = id;
        this.practitionerId = practitionerId;
        this.readBy = readBy;
        this.patientId = patientId;
        this.readAt = readAt;
        this.access = access;
        this.attachmentId = attachmentId;
    }

    /** The record was read whole, by that practitioner logged in as {@code readBy}. */
    public static RecordReading ofRecord(PractitionerId practitionerId, String readBy, PatientId patientId,
            LocalDateTime readAt, ReadAccess access) {
        return new RecordReading(RecordReadingId.newId(), practitionerId, requireUser(readBy), patientId, readAt,
                access, null);
    }

    /** A file attached to the record was read, by that practitioner logged in as {@code readBy}. */
    public static RecordReading ofAttachment(PractitionerId practitionerId, String readBy, PatientId patientId,
            LocalDateTime readAt, ReadAccess access, AttachmentId attachmentId) {
        if (attachmentId == null) {
            throw new IllegalArgumentException("A reading of an attachment needs the attachment");
        }
        return new RecordReading(RecordReadingId.newId(), practitionerId, requireUser(readBy), patientId, readAt,
                access, attachmentId);
    }

    /**
     * A reading as it was recorded, for reading it back. The user is null for the readings recorded
     * before users were (D-71).
     */
    public static RecordReading restore(RecordReadingId id, PractitionerId practitionerId, String readBy,
            PatientId patientId, LocalDateTime readAt, ReadAccess access, AttachmentId attachmentId) {
        return new RecordReading(id, practitionerId, readBy, patientId, readAt, access, attachmentId);
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

    public PractitionerId practitionerId() {
        return practitionerId;
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
}
