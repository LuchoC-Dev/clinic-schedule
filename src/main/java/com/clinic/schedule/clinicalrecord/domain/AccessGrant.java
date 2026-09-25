package com.clinic.schedule.clinicalrecord.domain;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;

/**
 * Access to a patient's clinical record granted to a practitioner who asked for it to report a
 * check (D-54): who, for which patient, because of which check, when, until when, why, and how it
 * was approved. It is kept as a record of who opened whose history, whether or not it is still
 * valid.
 *
 * <p>Plain domain object and immutable. How one is granted — which check allows asking, how long
 * it lasts, whether a reason is required — is {@link AccessGrantPolicy}'s.
 */
public class AccessGrant {

    /** As long as the column that stores it (D-38). */
    public static final int MAX_REASON_LENGTH = 500;

    private final AccessGrantId id;
    private final PatientId patientId;
    private final PractitionerId practitionerId;
    private final CheckId checkId;
    private final LocalDateTime grantedAt;
    private final LocalDate validUntil;
    private final String reason;
    private final Approval approval;

    private AccessGrant(AccessGrantId id, PatientId patientId, PractitionerId practitionerId, CheckId checkId,
            LocalDateTime grantedAt, LocalDate validUntil, String reason, Approval approval) {
        this.id = id;
        this.patientId = patientId;
        this.practitionerId = practitionerId;
        this.checkId = checkId;
        this.grantedAt = grantedAt;
        this.validUntil = validUntil;
        this.reason = reason;
        this.approval = approval;
    }

    /**
     * A grant, checked in full: both how the policy makes a new one and how the storage adapter
     * reads one back. Public because the adapter lives in another package.
     *
     * @param reason null or blank when none was given
     */
    public static AccessGrant restore(AccessGrantId id, PatientId patientId, PractitionerId practitionerId,
            CheckId checkId, LocalDateTime grantedAt, LocalDate validUntil, String reason, Approval approval) {
        if (id == null || patientId == null || practitionerId == null || checkId == null || grantedAt == null
                || validUntil == null || approval == null) {
            throw new IllegalArgumentException(
                    "An access grant needs an id, a patient, a practitioner, a check, its dates and its approval");
        }
        if (validUntil.isBefore(grantedAt.toLocalDate())) {
            throw new IllegalArgumentException("An access grant cannot end before it is granted");
        }
        String stripped = reason == null || reason.isBlank() ? null : reason.strip();
        if (stripped != null && stripped.length() > MAX_REASON_LENGTH) {
            throw new IllegalArgumentException("The reason of an access grant is at most " + MAX_REASON_LENGTH + " characters");
        }
        return new AccessGrant(id, patientId, practitionerId, checkId, grantedAt, validUntil, stripped, approval);
    }

    /** Whether it still opens the record on that day: from the day it was granted until its last day, included. */
    public boolean isValidOn(LocalDate day) {
        return !day.isBefore(grantedAt.toLocalDate()) && !day.isAfter(validUntil);
    }

    public AccessGrantId id() {
        return id;
    }

    public PatientId patientId() {
        return patientId;
    }

    public PractitionerId practitionerId() {
        return practitionerId;
    }

    /** The pending check the practitioner asked to report. */
    public CheckId checkId() {
        return checkId;
    }

    public LocalDateTime grantedAt() {
        return grantedAt;
    }

    /** The last day it opens the record, included. */
    public LocalDate validUntil() {
        return validUntil;
    }

    public Optional<String> reason() {
        return Optional.ofNullable(reason);
    }

    public Approval approval() {
        return approval;
    }
}
