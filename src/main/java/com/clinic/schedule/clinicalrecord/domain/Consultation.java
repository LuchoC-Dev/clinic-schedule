package com.clinic.schedule.clinicalrecord.domain;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * A consultation as the practitioner sent it: who saw whom and when, what was consulted, and
 * the checks, prescriptions and study orders that came out of it — plus the addenda written
 * about it afterwards.
 *
 * <p>Plain domain object, with no persistence or framework annotations (D-16).
 *
 * <p>It is immutable, and what was sent never changes: once sent, a consultation is part of the
 * patient's clinical record (see the context). The only operation, {@link #withAddendum},
 * returns a new consultation with one more addendum at the end and everything else as it was
 * (D-52).
 *
 * <p>Who may record it or add to it is not its business: that is the access rule of
 * {@link ClinicalRecordAccessPolicy}, applied by the use cases (D-47).
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
    private final List<Addendum> addenda;

    private Consultation(ConsultationId id, PatientId patientId, PractitionerId practitionerId,
            LocalDateTime recordedAt, String notes, List<ConsultationItem> items, List<Addendum> addenda) {
        this.id = id;
        this.patientId = patientId;
        this.practitionerId = practitionerId;
        this.recordedAt = recordedAt;
        this.notes = notes;
        this.items = items;
        this.addenda = addenda;
    }

    /**
     * A new consultation, checked in full, with no addenda yet.
     *
     * @param items in the order the practitioner wrote them; may be empty
     */
    public static Consultation record(ConsultationId id, PatientId patientId, PractitionerId practitionerId,
            LocalDateTime recordedAt, String notes, List<ConsultationItem> items) {
        return restore(id, patientId, practitionerId, recordedAt, notes, items, List.of());
    }

    /**
     * A consultation read back from storage, with its addenda. It checks the same as
     * {@link #record}, and each addendum as {@link #withAddendum} does, so there is no way to
     * obtain one that skips the checks (D-47). Public because the storage adapter lives in
     * another package, as with {@code Appointment.restore}.
     *
     * @param addenda in the order they were written
     */
    public static Consultation restore(ConsultationId id, PatientId patientId, PractitionerId practitionerId,
            LocalDateTime recordedAt, String notes, List<ConsultationItem> items, List<Addendum> addenda) {
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
        if (addenda == null || addenda.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("The addenda of a consultation cannot be missing");
        }
        Consultation consultation = new Consultation(id, patientId, practitionerId, recordedAt, stripped,
                List.copyOf(items), List.of());
        for (Addendum addendum : addenda) {
            consultation = consultation.withAddendum(addendum);
        }
        return consultation;
    }

    /**
     * The same consultation with one more addendum at the end. Nothing already written changes.
     *
     * <p>An addendum is written after the consultation and after the addenda before it, and each
     * one is added once.
     */
    public Consultation withAddendum(Addendum addendum) {
        if (addendum == null) {
            throw new IllegalArgumentException("An addendum is required");
        }
        LocalDateTime latest = addenda.isEmpty() ? recordedAt : addenda.getLast().writtenAt();
        if (addendum.writtenAt().isBefore(latest)) {
            throw new IllegalArgumentException("An addendum is written after the consultation and the addenda before it");
        }
        if (hasAddendum(addendum.id())) {
            throw new IllegalArgumentException("The consultation already has addendum " + addendum.id().value());
        }
        List<Addendum> more = new ArrayList<>(addenda);
        more.add(addendum);
        return new Consultation(id, patientId, practitionerId, recordedAt, notes, items, List.copyOf(more));
    }

    public boolean hasAddendum(AddendumId addendumId) {
        return addenda.stream().anyMatch(addendum -> addendum.id().equals(addendumId));
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

    /** In the order they were written. */
    public List<Addendum> addenda() {
        return addenda;
    }
}
