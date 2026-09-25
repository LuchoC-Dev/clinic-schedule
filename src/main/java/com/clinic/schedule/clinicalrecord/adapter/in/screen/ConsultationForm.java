package com.clinic.schedule.clinicalrecord.adapter.in.screen;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.clinic.schedule.clinicalrecord.application.usecase.RecordConsultationUseCase;
import com.clinic.schedule.clinicalrecord.domain.ConsultationItem;
import com.clinic.schedule.clinicalrecord.domain.ItemKind;

/**
 * The consultation form as the screen sends it: the notes; one kind and one description per row
 * of items; and one description and one result per row of checks, in order. A refused form travels
 * back to the screen in this shape, so nothing typed is lost (D-51, D-53).
 *
 * <p>The rows are parallel lists because that is how an HTML form without JavaScript sends a
 * table of fields.
 */
record ConsultationForm(UUID patientId, String notes, List<String> kind, List<String> description,
        List<String> checkDescription, List<String> checkResult) {

    /** Rows offered below the ones filled in, and at least this many rows in all, in each table. */
    static final int EMPTY_ROWS = 3;
    static final int MIN_ROWS = 5;

    ConsultationForm {
        kind = kind == null ? List.of() : kind;
        description = description == null ? List.of() : description;
        checkDescription = checkDescription == null ? List.of() : checkDescription;
        checkResult = checkResult == null ? List.of() : checkResult;
    }

    static ConsultationForm empty(UUID patientId) {
        return new ConsultationForm(patientId, null, List.of(), List.of(), List.of(), List.of());
    }

    /** The same form with {@link #EMPTY_ROWS} more empty rows in each table: what "Más renglones" asks for. */
    ConsultationForm withMoreRows() {
        int items = Math.max(kind.size(), description.size()) + EMPTY_ROWS;
        int checks = Math.max(checkDescription.size(), checkResult.size()) + EMPTY_ROWS;
        return new ConsultationForm(patientId, notes, padded(kind, items), padded(description, items),
                padded(checkDescription, checks), padded(checkResult, checks));
    }

    /** The item rows to show: the filled ones first, then empty ones; see {@link #toShow}. */
    List<Row> itemRowsToShow() {
        return toShow(rows(kind, description), Math.max(kind.size(), description.size()));
    }

    /** The check rows to show: the filled ones first, then empty ones; see {@link #toShow}. */
    List<Row> checkRowsToShow() {
        return toShow(rows(checkDescription, checkResult), Math.max(checkDescription.size(), checkResult.size()));
    }

    /**
     * Whether some row is half filled in: an item with a kind and no text or a text and no kind, or
     * a check with a result and no description of what was done. Refused, not guessed. A check with
     * a description and no result is whole: its result is pending.
     */
    boolean hasHalfFilledRow() {
        return rows(kind, description).stream().anyMatch(row -> row.first().isBlank() || row.second().isBlank())
                || rows(checkDescription, checkResult).stream().anyMatch(row -> row.first().isBlank());
    }

    /**
     * The items, from the filled rows.
     *
     * @throws IllegalArgumentException for a kind the screen does not offer, or an item too long
     */
    List<ConsultationItem> items() {
        return rows(kind, description).stream()
                .map(row -> new ConsultationItem(ItemKind.valueOf(row.first()), row.second()))
                .toList();
    }

    /** The checks, from the filled rows; a blank result is a pending one. */
    List<RecordConsultationUseCase.CheckDone> checks() {
        return rows(checkDescription, checkResult).stream()
                .map(row -> new RecordConsultationUseCase.CheckDone(row.first(), row.second()))
                .toList();
    }

    /** The rows with something typed in, as they were typed. */
    private static List<Row> rows(List<String> firsts, List<String> seconds) {
        List<Row> filled = new ArrayList<>();
        for (int i = 0; i < Math.max(firsts.size(), seconds.size()); i++) {
            Row row = new Row(valueAt(firsts, i), valueAt(seconds, i));
            if (!row.isEmpty()) {
                filled.add(row);
            }
        }
        return filled;
    }

    /**
     * The filled rows, then empty ones: as many as were sent, at least {@link #EMPTY_ROWS} empty
     * ones below the filled ones, and {@link #MIN_ROWS} in all.
     */
    private static List<Row> toShow(List<Row> filled, int sent) {
        List<Row> rows = new ArrayList<>(filled);
        int total = Math.max(Math.max(MIN_ROWS, sent), rows.size() + EMPTY_ROWS);
        while (rows.size() < total) {
            rows.add(new Row("", ""));
        }
        return rows;
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

    /**
     * One row of a table: an item's kind and text, or a check's description and result. Public
     * accessors, so the template can read it.
     */
    record Row(String first, String second) {

        boolean isEmpty() {
            return first.isBlank() && second.isBlank();
        }
    }
}
