package com.clinic.schedule.clinicalrecord.domain;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import com.clinic.schedule.clinicalrecord.domain.exception.ResultAlreadyRecordedException;

/**
 * A check done to a patient — a pulse taken, a blood sample drawn, an electrocardiogram — its
 * result, once there is one, and the addenda written about it afterwards (D-53, D-58).
 *
 * <p>A check is what is done once and reported once, by one author: an electrocardiogram is one
 * check, whose report includes the heart rate the device measures; the heartbeat heard with a
 * stethoscope during the consultation is another. It is done during a consultation or, as a
 * precaution for emergencies, without one.
 *
 * <p>Its states follow from the result: without one, the result is pending; with one, the check
 * is reported, and the result never changes. A check whose result is known at once is born
 * reported. A mistake in it — a wrong result, say — is corrected with an addendum, as a
 * consultation is (D-52): added at the end, nothing already written changed.
 *
 * <p>Whoever did it is one of the practice's practitioners today. Checks done elsewhere will come
 * in through an anti-corruption layer later; nothing else here assumes where a check was done.
 *
 * <p>Plain domain object and immutable, like {@link Consultation}. Who may record a check, its
 * result or an addendum is the clinical record's access rule, applied by the use cases.
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
    private final List<Addendum> addenda;

    private Check(CheckId id, PatientId patientId, PractitionerId performedBy, LocalDateTime performedAt,
            String description, ConsultationId consultationId, CheckResult result, List<Addendum> addenda) {
        this.id = id;
        this.patientId = patientId;
        this.performedBy = performedBy;
        this.performedAt = performedAt;
        this.description = description;
        this.consultationId = consultationId;
        this.result = result;
        this.addenda = addenda;
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

    /** A check just done whose result is known at once, in words: reported by whoever did it, at that time. */
    public static Check performWithResult(CheckId id, PatientId patientId, PractitionerId performedBy,
            LocalDateTime performedAt, String description, ConsultationId consultationId, String result) {
        return restore(id, patientId, performedBy, performedAt, description, consultationId,
                new CheckResult(performedBy, performedAt, result));
    }

    /** A stored check without addenda; see {@link #restore(CheckId, PatientId, PractitionerId, LocalDateTime, String, ConsultationId, CheckResult, List)}. */
    public static Check restore(CheckId id, PatientId patientId, PractitionerId performedBy,
            LocalDateTime performedAt, String description, ConsultationId consultationId, CheckResult result) {
        return restore(id, patientId, performedBy, performedAt, description, consultationId, result, List.of());
    }

    /**
     * A check read back from storage, with its addenda, checked the same way, so there is no way
     * to obtain an invalid one. Public because the storage adapter lives in another package.
     *
     * @param result null while it is pending
     * @param addenda in the order they were written
     */
    public static Check restore(CheckId id, PatientId patientId, PractitionerId performedBy,
            LocalDateTime performedAt, String description, ConsultationId consultationId, CheckResult result,
            List<Addendum> addenda) {
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
        if (addenda == null || addenda.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("The addenda of a check cannot be missing");
        }
        Check check = new Check(id, patientId, performedBy, performedAt, stripped, consultationId, null, List.of());
        // Whether a result without text had its attachments was checked when it was written.
        if (result != null) {
            check = check.withResult(result, true);
        }
        for (Addendum addendum : addenda) {
            check = check.withAddendum(addendum);
        }
        return check;
    }

    /**
     * The same check with its result, in words. A check has one result, and it never changes: a
     * second one is refused. The result is written when the check was done or later.
     *
     * @throws ResultAlreadyRecordedException when the check already has one
     */
    public Check withResult(CheckResult newResult) {
        return withResult(newResult, false);
    }

    /**
     * The same check with its result, which may be only its attachments: without text, the check
     * must have at least one (D-57).
     *
     * @param hasAttachments whether files are attached to this check
     * @throws ResultAlreadyRecordedException when the check already has one
     */
    public Check withResult(CheckResult newResult, boolean hasAttachments) {
        if (newResult == null) {
            throw new IllegalArgumentException("A result is required");
        }
        if (!newResult.hasText() && !hasAttachments) {
            throw new IllegalArgumentException("A result without text needs the check to have an attachment");
        }
        if (result != null) {
            throw new ResultAlreadyRecordedException(id);
        }
        if (newResult.writtenAt().isBefore(performedAt)) {
            throw new IllegalArgumentException("A result is written after the check was done");
        }
        return new Check(id, patientId, performedBy, performedAt, description, consultationId, newResult, addenda);
    }

    /**
     * The same check with one more addendum at the end, pending or reported alike. Nothing already
     * written changes (D-58). An addendum is written after the check was done and after the addenda
     * before it, and each one is added once.
     */
    public Check withAddendum(Addendum addendum) {
        if (addendum == null) {
            throw new IllegalArgumentException("An addendum is required");
        }
        LocalDateTime latest = addenda.isEmpty() ? performedAt : addenda.getLast().writtenAt();
        if (addendum.writtenAt().isBefore(latest)) {
            throw new IllegalArgumentException("An addendum is written after the check and the addenda before it");
        }
        if (hasAddendum(addendum.id())) {
            throw new IllegalArgumentException("The check already has addendum " + addendum.id().value());
        }
        List<Addendum> more = new ArrayList<>(addenda);
        more.add(addendum);
        return new Check(id, patientId, performedBy, performedAt, description, consultationId, result,
                List.copyOf(more));
    }

    public boolean hasAddendum(AddendumId addendumId) {
        return addenda.stream().anyMatch(addendum -> addendum.id().equals(addendumId));
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

    /** In the order they were written. */
    public List<Addendum> addenda() {
        return addenda;
    }
}
