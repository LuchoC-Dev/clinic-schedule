package com.clinic.schedule.consultation.adapter.in.screen;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import com.clinic.schedule.consultation.domain.Consultation;
import com.clinic.schedule.consultation.domain.ItemKind;

/**
 * One consultation of a clinical record screen: when, by whom, the notes, the items with their
 * kind put into words, and the addenda written afterwards.
 */
record ClinicalRecordEntry(UUID id, String recordedAt, String practitioner, String notes, List<Line> items,
        List<Note> addenda) {

    private static final DateTimeFormatter WHEN =
            DateTimeFormatter.ofPattern("EEE dd/MM/yyyy HH:mm", Locale.forLanguageTag("es-AR"));

    /** One item: its kind in Spanish and its text. */
    record Line(String kind, String description) {
    }

    /** One addendum: when, by whom, and its text. */
    record Note(String writtenAt, String author, String text) {
    }

    /** @param names every practitioner's name by id */
    static ClinicalRecordEntry of(Consultation consultation, Map<UUID, String> names) {
        return new ClinicalRecordEntry(
                consultation.id().value(),
                format(consultation.recordedAt()),
                nameOf(names, consultation.practitionerId().value()),
                consultation.notes(),
                consultation.items().stream()
                        .map(item -> new Line(labelOf(item.kind()), item.description()))
                        .toList(),
                consultation.addenda().stream()
                        .map(addendum -> new Note(format(addendum.writtenAt()),
                                nameOf(names, addendum.author().value()), addendum.text()))
                        .toList());
    }

    /** What the staff reads for each kind (D-13). */
    static String labelOf(ItemKind kind) {
        return switch (kind) {
            case CHECK -> "Chequeo";
            case PRESCRIPTION -> "Receta";
            case STUDY_ORDER -> "Orden de estudio";
        };
    }

    private static String format(LocalDateTime when) {
        return when.format(WHEN);
    }

    private static String nameOf(Map<UUID, String> names, UUID practitionerId) {
        String name = names.get(practitionerId);
        return name == null ? "(profesional desconocido)" : name;
    }
}
