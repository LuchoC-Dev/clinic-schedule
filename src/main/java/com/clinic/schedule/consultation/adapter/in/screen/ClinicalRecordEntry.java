package com.clinic.schedule.consultation.adapter.in.screen;

import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

import com.clinic.schedule.consultation.domain.Consultation;
import com.clinic.schedule.consultation.domain.ItemKind;

/**
 * One consultation of a clinical record screen: when, by whom, the notes, and the items with
 * their kind put into words.
 */
record ClinicalRecordEntry(String recordedAt, String practitioner, String notes, List<Line> items) {

    private static final DateTimeFormatter WHEN =
            DateTimeFormatter.ofPattern("EEE dd/MM/yyyy HH:mm", Locale.forLanguageTag("es-AR"));

    /** One item: its kind in Spanish and its text. */
    record Line(String kind, String description) {
    }

    static ClinicalRecordEntry of(Consultation consultation, String practitioner) {
        return new ClinicalRecordEntry(
                consultation.recordedAt().format(WHEN),
                practitioner == null ? "(profesional desconocido)" : practitioner,
                consultation.notes(),
                consultation.items().stream()
                        .map(item -> new Line(labelOf(item.kind()), item.description()))
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
}
