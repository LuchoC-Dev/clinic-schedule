package com.clinic.schedule.consultation.adapter.in.screen;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.clinic.schedule.consultation.domain.ConsultationItem;
import com.clinic.schedule.consultation.domain.ItemKind;

/**
 * The consultation form as the screen sends it: the notes, and one kind and one description per
 * row of items, in order. A refused form travels back to the screen in this shape, so nothing
 * typed is lost (D-51).
 *
 * <p>The rows are parallel lists because that is how an HTML form without JavaScript sends a
 * table of fields.
 */
record ConsultationForm(UUID patientId, String notes, List<String> kind, List<String> description) {

    /** Rows offered below the ones filled in, and at least this many rows in all. */
    static final int EMPTY_ROWS = 3;
    static final int MIN_ROWS = 5;

    ConsultationForm {
        kind = kind == null ? List.of() : kind;
        description = description == null ? List.of() : description;
    }

    static ConsultationForm empty(UUID patientId) {
        return new ConsultationForm(patientId, null, List.of(), List.of());
    }

    /** The rows with something typed in, as they were typed, for the screen to show again. */
    List<Row> filledRows() {
        List<Row> filled = new ArrayList<>();
        for (int i = 0; i < Math.max(kind.size(), description.size()); i++) {
            Row row = new Row(valueAt(kind, i), valueAt(description, i));
            if (!row.isEmpty()) {
                filled.add(row);
            }
        }
        return filled;
    }

    /** The same form with {@link #EMPTY_ROWS} more empty rows: what "Más renglones" asks for. */
    ConsultationForm withMoreRows() {
        int rows = Math.max(kind.size(), description.size()) + EMPTY_ROWS;
        return new ConsultationForm(patientId, notes, padded(kind, rows), padded(description, rows));
    }

    /**
     * The rows to show: the filled ones first, then empty ones. As many as were sent, and at
     * least {@link #EMPTY_ROWS} empty ones below the filled ones, and {@link #MIN_ROWS} in all.
     */
    List<Row> rowsToShow() {
        List<Row> rows = new ArrayList<>(filledRows());
        int sent = Math.max(kind.size(), description.size());
        int total = Math.max(Math.max(MIN_ROWS, sent), rows.size() + EMPTY_ROWS);
        while (rows.size() < total) {
            rows.add(new Row("", ""));
        }
        return rows;
    }

    /** Whether some row has a kind without a text or a text without a kind: refused, not guessed. */
    boolean hasHalfFilledRow() {
        return filledRows().stream().anyMatch(row -> row.kind().isBlank() || row.description().isBlank());
    }

    /**
     * The items, from the filled rows.
     *
     * @throws IllegalArgumentException for a kind the screen does not offer, or an item too long
     */
    List<ConsultationItem> items() {
        return filledRows().stream()
                .map(row -> new ConsultationItem(ItemKind.valueOf(row.kind()), row.description()))
                .toList();
    }

    private static List<String> padded(List<String> values, int size) {
        List<String> padded = new ArrayList<>(values);
        while (padded.size() < size) {
            padded.add("");
        }
        return padded;
    }

    private static String valueAt(List<String> values, int index) {
        String value = index < values.size() ? values.get(index) : null;
        return value == null ? "" : value;
    }

    /** One row of items. Public accessors, so the template can read it. */
    record Row(String kind, String description) {

        boolean isEmpty() {
            return kind.isBlank() && description.isBlank();
        }
    }
}
