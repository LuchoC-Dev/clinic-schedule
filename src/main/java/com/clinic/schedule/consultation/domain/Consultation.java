package com.clinic.schedule.consultation.domain;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

/**
 * A consultation as the practitioner sent it: who saw whom and when, what was consulted, and
 * the checks, prescriptions and study orders that came out of it.
 *
 * <p>Plain domain object, with no persistence or framework annotations (D-16).
 *
 * <p>It is immutable and has no operation that changes it: once sent, a consultation is part
 * of the patient's clinical record and what was written never changes (see the context). The
 * way to correct it — an addendum with its date and author — adds to it instead.
 *
 * <p>Who may record it is not its business: that is the access rule of
 * {@link ClinicalRecordAccessPolicy}, applied by the use case (D-47).
 */
public class Consultation {

    /** As long as the column that stores it (D-38). */
    public static final int MAX_NOTES_LENGTH = 10_000;

    /** More than any consultation holds; a limit so a request cannot grow without bound (D-38). */
    public static final int MAX_ITEMS = 50;

    private final ConsultationId id;
    private final PatientId patientId;
    private final PractitionerId practitionerId;
    private final LocalDateTime recordedAt;
    private final String notes;
    private final List<ConsultationItem> items;

    private Consultation(ConsultationId id, PatientId patientId, PractitionerId practitionerId,
            LocalDateTime recordedAt, String notes, List<ConsultationItem> items) {
        this.id = id;
        this.patientId = patientId;
        this.practitionerId = practitionerId;
        this.recordedAt = recordedAt;
        this.notes = notes;
        this.items = items;
    }

    /**
     * A consultation, checked in full. It is both how a new one is made and how the storage
     * adapter reads one back: there is no way to obtain one that skips the checks (D-47).
     *
     * @param items in the order the practitioner wrote them; may be empty
     */
    public static Consultation record(ConsultationId id, PatientId patientId, PractitionerId practitionerId,
            LocalDateTime recordedAt, String notes, List<ConsultationItem> items) {
        if (id == null || patientId == null || practitionerId == null || recordedAt == null) {
            throw new IllegalArgumentException("A consultation needs an id, a patient, a practitioner and a time");
        }
        if (notes == null || notes.isBlank()) {
            throw new IllegalArgumentException("A consultation needs notes on what was consulted");
        }
        String stripped = notes.strip();
        if (stripped.length() > MAX_NOTES_LENGTH) {
            throw new IllegalArgumentException("The notes of a consultation are at most " + MAX_NOTES_LENGTH + " characters");
        }
        // Not items.contains(null): an immutable list answers that question with an exception.
        if (items == null || items.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("The items of a consultation cannot be missing");
        }
        if (items.size() > MAX_ITEMS) {
            throw new IllegalArgumentException("A consultation has at most " + MAX_ITEMS + " items");
        }
        return new Consultation(id, patientId, practitionerId, recordedAt, stripped, List.copyOf(items));
    }

    public ConsultationId id() {
        return id;
    }

    public PatientId patientId() {
        return patientId;
    }

    public PractitionerId practitionerId() {
        return practitionerId;
    }

    /** When it was sent, by the practice's clock. */
    public LocalDateTime recordedAt() {
        return recordedAt;
    }

    public String notes() {
        return notes;
    }

    public List<ConsultationItem> items() {
        return items;
    }
}
