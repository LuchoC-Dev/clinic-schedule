package com.clinic.schedule.clinicalrecord.domain;

import java.time.LocalDateTime;
import java.util.Optional;

/**
 * Someone tried to reach clinical content and was refused (D-72): who — the user, and the
 * practitioner they stand for, if any —, when, what they tried — a record, an attached file, a
 * clinical route —, and whose record, when that is known. Nothing was handed over, so it is not a
 * reading (D-59); it is kept to notice someone probing records that are not theirs.
 *
 * <p>Plain domain object and immutable. Only ever written; the admins read them.
 */
public class RecordRefusal {

    /** As long as the column that stores it; a longer route is cut there. */
    static final int MAX_ROUTE_LENGTH = 300;

    private final RecordRefusalId id;
    private final String refusedBy;
    private final PractitionerId practitionerId;
    private final LocalDateTime refusedAt;
    private final RefusedAttempt attempt;
    private final PatientId patientId;
    private final AttachmentId attachmentId;
    private final String route;

    private RecordRefusal(RecordRefusalId id, String refusedBy, PractitionerId practitionerId, LocalDateTime refusedAt,
            RefusedAttempt attempt, PatientId patientId, AttachmentId attachmentId, String route) {
        if (id == null || refusedBy == null || refusedBy.isBlank() || refusedAt == null || attempt == null) {
            throw new IllegalArgumentException("A refusal needs an id, who was refused, when, and what they tried");
        }
        this.id = id;
        this.refusedBy = refusedBy;
        this.practitionerId = practitionerId;
        this.refusedAt = refusedAt;
        this.attempt = attempt;
        this.patientId = patientId;
        this.attachmentId = attachmentId;
        this.route = route;
    }

    /** A practitioner — or a user who is none, when that is who asked — was refused a patient's record. */
    public static RecordRefusal ofRecord(String refusedBy, PractitionerId practitionerId, PatientId patientId,
            LocalDateTime refusedAt) {
        if (patientId == null) {
            throw new IllegalArgumentException("A refused record is some patient's");
        }
        return new RecordRefusal(RecordRefusalId.newId(), refusedBy, practitionerId, refusedAt, RefusedAttempt.RECORD,
                patientId, null, null);
    }

    /**
     * An attached file was refused.
     *
     * @param patientId null when the file does not exist, so whose it would be is unknown
     */
    public static RecordRefusal ofAttachment(String refusedBy, PractitionerId practitionerId, AttachmentId attachmentId,
            PatientId patientId, LocalDateTime refusedAt) {
        if (attachmentId == null) {
            throw new IllegalArgumentException("A refused file is some file");
        }
        return new RecordRefusal(RecordRefusalId.newId(), refusedBy, practitionerId, refusedAt, RefusedAttempt.ATTACHMENT,
                patientId, attachmentId, null);
    }

    /**
     * A clinical route was refused to the user's role.
     *
     * @param route the method and the path, such as {@code GET /clinical-records/…}
     * @param patientId the patient the route names, or null when it names none
     */
    public static RecordRefusal ofRoute(String refusedBy, PractitionerId practitionerId, String route,
            PatientId patientId, LocalDateTime refusedAt) {
        if (route == null || route.isBlank()) {
            throw new IllegalArgumentException("A refused route is some route");
        }
        String kept = route.length() > MAX_ROUTE_LENGTH ? route.substring(0, MAX_ROUTE_LENGTH) : route;
        return new RecordRefusal(RecordRefusalId.newId(), refusedBy, practitionerId, refusedAt, RefusedAttempt.ROUTE,
                patientId, null, kept);
    }

    /** A refusal as it was recorded, for reading it back. */
    public static RecordRefusal restore(RecordRefusalId id, String refusedBy, PractitionerId practitionerId, LocalDateTime refusedAt,
            RefusedAttempt attempt, PatientId patientId, AttachmentId attachmentId, String route) {
        return new RecordRefusal(id, refusedBy, practitionerId, refusedAt, attempt, patientId, attachmentId, route);
    }

    public RecordRefusalId id() {
        return id;
    }

    /** The username of whoever was refused. */
    public String refusedBy() {
        return refusedBy;
    }

    /** Empty when whoever was refused is not a practitioner. */
    public Optional<PractitionerId> practitionerId() {
        return Optional.ofNullable(practitionerId);
    }

    public LocalDateTime refusedAt() {
        return refusedAt;
    }

    public RefusedAttempt attempt() {
        return attempt;
    }

    /** Empty when it is not known whose record it was. */
    public Optional<PatientId> patientId() {
        return Optional.ofNullable(patientId);
    }

    public Optional<AttachmentId> attachmentId() {
        return Optional.ofNullable(attachmentId);
    }

    /** Only for a refused route. */
    public Optional<String> route() {
        return Optional.ofNullable(route);
    }
}
