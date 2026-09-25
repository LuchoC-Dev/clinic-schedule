package com.clinic.schedule.clinicalrecord.adapter.in.screen;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.clinic.schedule.clinicalrecord.application.usecase.RecordConsultationUseCase;
import com.clinic.schedule.clinicalrecord.domain.CheckOrderId;
import com.clinic.schedule.clinicalrecord.domain.ConsultationItem;
import com.clinic.schedule.clinicalrecord.domain.ItemKind;

/**
 * The consultation form as the screen sends it: the notes; one kind and one description per row of
 * items; one description, one result and one order fulfilled per row of checks; and one
 * description per row of orders issued, in order. A refused form travels back to the screen in this
 * shape, so nothing typed is lost (D-51, D-53, D-55).
 *
 * <p>The rows are parallel lists because that is how an HTML form without JavaScript sends a
 * table of fields.
 */
record ConsultationForm(UUID patientId, String notes, List<String> kind, List<String> description,
        List<String> checkDescription, List<String> checkResult, List<String> checkOrder,
        List<String> orderDescription) {

    /** Rows offered below the ones filled in, and at least this many rows in all, in each table. */
    static final int EMPTY_ROWS = 3;
    static final int MIN_ROWS = 5;

    ConsultationForm {
        kind = orEmpty(kind);
        description = orEmpty(description);
        checkDescription = orEmpty(checkDescription);
        checkResult = orEmpty(checkResult);
        checkOrder = orEmpty(checkOrder);
        orderDescription = orEmpty(orderDescription);
    }

    static ConsultationForm empty(UUID patientId) {
        return new ConsultationForm(patientId, null, null, null, null, null, null, null);
    }

    /** The same form with {@link #EMPTY_ROWS} more empty rows in each table: what "Más renglones" asks for. */
    ConsultationForm withMoreRows() {
        int items = size(kind, description) + EMPTY_ROWS;
        int checks = size(checkDescription, checkResult, checkOrder) + EMPTY_ROWS;
        int orders = orderDescription.size() + EMPTY_ROWS;
        return new ConsultationForm(patientId, notes, padded(kind, items), padded(description, items),
                padded(checkDescription, checks), padded(checkResult, checks), padded(checkOrder, checks),
                padded(orderDescription, orders));
    }

    /** The item rows to show: kind and text. */
    List<Row> itemRowsToShow() {
        return toShow(rows(kind, description, List.of()), size(kind, description));
    }

    /** The check rows to show: what was done, its result, the order it fulfils. */
    List<Row> checkRowsToShow() {
        return toShow(rows(checkDescription, checkResult, checkOrder), size(checkDescription, checkResult, checkOrder));
    }

    /** The order rows to show: what each order asks for. */
    List<Row> orderRowsToShow() {
        return toShow(rows(orderDescription, List.of(), List.of()), orderDescription.size());
    }

    /**
     * Whether some row is half filled in: an item with a kind and no text or a text and no kind, or a
     * check with a result and neither a description of what was done nor an order it fulfils. Refused,
     * not guessed. A check with a description and no result is whole: its result is pending; one with
     * only an order is what the order asks for.
     */
    boolean hasHalfFilledRow() {
        return rows(kind, description, List.of()).stream()
                        .anyMatch(row -> row.first().isBlank() || row.second().isBlank())
                || rows(checkDescription, checkResult, checkOrder).stream()
                        .anyMatch(row -> row.first().isBlank() && row.third().isBlank());
    }

    /**
     * The items, from the filled rows.
     *
     * @throws IllegalArgumentException for a kind the screen does not offer, or an item too long
     */
    List<ConsultationItem> items() {
        return rows(kind, description, List.of()).stream()
                .map(row -> new ConsultationItem(ItemKind.valueOf(row.first()), row.second()))
                .toList();
    }

    /**
     * The checks, from the filled rows; a blank result is a pending one.
     *
     * @throws IllegalArgumentException for an order that is not an id
     */
    List<RecordConsultationUseCase.CheckDone> checks() {
        return rows(checkDescription, checkResult, checkOrder).stream()
                .map(row -> new RecordConsultationUseCase.CheckDone(row.first(), row.second(),
                        row.third().isBlank() ? null : new CheckOrderId(UUID.fromString(row.third()))))
                .toList();
    }

    /** What each order issued asks for, from the filled rows. */
    List<String> orders() {
        return rows(orderDescription, List.of(), List.of()).stream().map(Row::first).toList();
    }

    /** The rows with something typed in, as they were typed. */
    private static List<Row> rows(List<String> firsts, List<String> seconds, List<String> thirds) {
        List<Row> filled = new ArrayList<>();
        for (int i = 0; i < size(firsts, seconds, thirds); i++) {
            Row row = new Row(valueAt(firsts, i), valueAt(seconds, i), valueAt(thirds, i));
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
            rows.add(new Row("", "", ""));
        }
        return rows;
    }

    @SafeVarargs
    private static int size(List<String>... columns) {
        int size = 0;
        for (List<String> column : columns) {
            size = Math.max(size, column.size());
        }
        return size;
    }

    private static List<String> orEmpty(List<String> values) {
        return values == null ? List.of() : values;
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
     * One row of a table: an item's kind and text; a check's description, result and order; or an
     * order's description. Public accessors, so the template can read it.
     */
    record Row(String first, String second, String third) {

        boolean isEmpty() {
            return first.isBlank() && second.isBlank() && third.isBlank();
        }
    }
}
