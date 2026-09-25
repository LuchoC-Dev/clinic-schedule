package com.clinic.schedule.clinicalrecord.adapter.in.screen;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import com.clinic.schedule.clinicalrecord.application.usecase.ViewClinicalRecordUseCase;
import com.clinic.schedule.clinicalrecord.domain.Check;
import com.clinic.schedule.clinicalrecord.domain.Consultation;
import com.clinic.schedule.clinicalrecord.domain.ItemKind;

/**
 * One entry of a clinical record screen, the most recent first: a consultation — when, by whom,
 * the notes, the items with their kind put into words, the checks done during it and the addenda
 * written afterwards — or a check done without a consultation. Exactly one of {@code consultation}
 * and {@code check} is set (D-53).
 */
record ClinicalRecordEntry(ConsultationView consultation, CheckEntry check) {

    private static final DateTimeFormatter WHEN =
            DateTimeFormatter.ofPattern("EEE dd/MM/yyyy HH:mm", Locale.forLanguageTag("es-AR"));

    /** A consultation as the screen shows it. */
    record ConsultationView(UUID id, String recordedAt, String practitioner, String notes, List<Line> items,
            List<CheckEntry> checks, List<Note> addenda) {
    }

    /** One item: its kind in Spanish and its text. */
    record Line(String kind, String description) {
    }

    /** One addendum: when, by whom, and its text. */
    record Note(String writtenAt, String author, String text) {
    }

    /** The whole record as entries, consultations and checks without one mixed by time, newest first. */
    static List<ClinicalRecordEntry> of(ViewClinicalRecordUseCase.ClinicalRecord record, Map<UUID, String> names) {
        record Timed(LocalDateTime at, ClinicalRecordEntry entry) {
        }
        List<Timed> timed = new ArrayList<>();
        for (Consultation consultation : record.consultations()) {
            List<CheckEntry> itsChecks = record.checks().stream()
                    .filter(check -> check.consultationId().equals(Optional.of(consultation.id())))
                    .sorted(Comparator.comparing(Check::performedAt))
                    .map(check -> CheckEntry.of(check, names))
                    .toList();
            timed.add(new Timed(consultation.recordedAt(),
                    new ClinicalRecordEntry(consultationOf(consultation, itsChecks, names), null)));
        }
        for (Check check : record.checks()) {
            if (check.consultationId().isEmpty()) {
                timed.add(new Timed(check.performedAt(), new ClinicalRecordEntry(null, CheckEntry.of(check, names))));
            }
        }
        return timed.stream()
                .sorted(Comparator.comparing(Timed::at).reversed())
                .map(Timed::entry)
                .toList();
    }

    private static ConsultationView consultationOf(Consultation consultation,
            List<CheckEntry> checks, Map<UUID, String> names) {
        return new ConsultationView(
                consultation.id().value(),
                format(consultation.recordedAt()),
                nameOf(names, consultation.practitionerId().value()),
                consultation.notes(),
                consultation.items().stream()
                        .map(item -> new Line(labelOf(item.kind()), item.description()))
                        .toList(),
                checks,
                consultation.addenda().stream()
                        .map(addendum -> new Note(format(addendum.writtenAt()),
                                nameOf(names, addendum.author().value()), addendum.text()))
                        .toList());
    }

    /** What the staff reads for each kind (D-13). */
    static String labelOf(ItemKind kind) {
        return switch (kind) {
            case PRESCRIPTION -> "Receta";
            case STUDY_ORDER -> "Orden de estudio";
        };
    }

    static String format(LocalDateTime when) {
        return when.format(WHEN);
    }

    static String nameOf(Map<UUID, String> names, UUID practitionerId) {
        String name = names.get(practitionerId);
        return name == null ? "(profesional desconocido)" : name;
    }
}
