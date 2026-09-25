package com.clinic.schedule.clinicalrecord.domain;

import java.time.LocalDateTime;
import java.util.Optional;

import com.clinic.schedule.clinicalrecord.domain.exception.ResultAlreadyRecordedException;

/**
 * A check done to a patient — a pulse taken, a blood sample drawn, an electrocardiogram — and its
 * result, once there is one (D-53).
 *
 * <p>A check is what is done once and reported once, by one author: an electrocardiogram is one
 * check, whose report includes the heart rate the device measures; the heartbeat heard with a
 * stethoscope during the consultation is another. It is done during a consultation or, as a
 * precaution for emergencies, without one.
 *
 * <p>Its states follow from the result: without one, the result is pending; with one, the check
 * is reported, and the result never changes. A check whose result is known at once is born
 * reported.
 *
 * <p>Whoever did it is one of the practice's practitioners today. Checks done elsewhere will come
 * in through an anti-corruption layer later; nothing else here assumes where a check was done.
 *
 * <p>Plain domain object and immutable, like {@link Consultation}. Who may record a check or its
 * result is the clinical record's access rule, applied by the use cases.
 */
public class Check {

    /** As long as the column that stores it, the same as an item (D-38). */
    public static final int MAX_DESCRIPTION_LENGTH = 2_000;

    private final CheckId id;
    private final PatientId patientId;
    private final PractitionerId performedBy;
    private final LocalDateTime performedAt;
    private final String description;
    private final ConsultationId consultationId;
    private final CheckResult result;

    private Check(CheckId id, PatientId patientId, PractitionerId performedBy, LocalDateTime performedAt,
            String description, ConsultationId consultationId, CheckResult result) {
        this.id = id;
        this.patientId = patientId;
        this.performedBy = performedBy;
        this.performedAt = performedAt;
        this.description = description;
        this.consultationId = consultationId;
        this.result = result;
    }

    /**
     * A check just done, whose result is pending.
     *
     * @param consultationId the consultation it was done in, or null when there was none
     */
    public static Check perform(CheckId id, PatientId patientId, PractitionerId performedBy,
            LocalDateTime performedAt, String description, ConsultationId consultationId) {
        return restore(id, patientId, performedBy, performedAt, description, consultationId, null);
    }

    /** A check just done whose result is known at once: reported by whoever did it, at that time. */
    public static Check performWithResult(CheckId id, PatientId patientId, PractitionerId performedBy,
            LocalDateTime performedAt, String description, ConsultationId consultationId, String result) {
        return restore(id, patientId, performedBy, performedAt, description, consultationId,
                new CheckResult(performedBy, performedAt, result));
    }

    /**
     * A check read back from storage, checked the same way, so there is no way to obtain an
     * invalid one. Public because the storage adapter lives in another package.
     *
     * @param result null while it is pending
     */
    public static Check restore(CheckId id, PatientId patientId, PractitionerId performedBy,
            LocalDateTime performedAt, String description, ConsultationId consultationId, CheckResult result) {
        if (id == null || patientId == null || performedBy == null || performedAt == null) {
            throw new IllegalArgumentException("A check needs an id, a patient, who did it and when");
        }
        if (description == null || description.isBlank()) {
            throw new IllegalArgumentException("A check needs a description of what was done");
        }
        String stripped = description.strip();
        if (stripped.length() > MAX_DESCRIPTION_LENGTH) {
            throw new IllegalArgumentException("A check is described in at most " + MAX_DESCRIPTION_LENGTH + " characters");
        }
        Check pending = new Check(id, patientId, performedBy, performedAt, stripped, consultationId, null);
        return result == null ? pending : pending.withResult(result);
    }

    /**
     * The same check with its result. A check has one result, and it never changes: a second one
     * is refused. The result is written when the check was done or later.
     *
     * @throws ResultAlreadyRecordedException when the check already has one
     */
    public Check withResult(CheckResult newResult) {
        if (newResult == null) {
            throw new IllegalArgumentException("A result is required");
        }
        if (result != null) {
            throw new ResultAlreadyRecordedException(id);
        }
        if (newResult.writtenAt().isBefore(performedAt)) {
            throw new IllegalArgumentException("A result is written after the check was done");
        }
        return new Check(id, patientId, performedBy, performedAt, description, consultationId, newResult);
    }

    public boolean isPending() {
        return result == null;
    }

    public CheckId id() {
        return id;
    }

    public PatientId patientId() {
        return patientId;
    }

    public PractitionerId performedBy() {
        return performedBy;
    }

    public LocalDateTime performedAt() {
        return performedAt;
    }

    public String description() {
        return description;
    }

    /** The consultation it was done in; empty for a check done without one. */
    public Optional<ConsultationId> consultationId() {
        return Optional.ofNullable(consultationId);
    }

    /** Empty while the result is pending. */
    public Optional<CheckResult> result() {
        return Optional.ofNullable(result);
    }
}
