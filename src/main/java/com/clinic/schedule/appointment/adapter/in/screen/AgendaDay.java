package com.clinic.schedule.appointment.adapter.in.screen;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

/**
 * One day of an agenda screen, with its heading in Spanish: the day view shows one, the week
 * view seven. A day with nothing booked still appears, so a week never has gaps.
 */
record AgendaDay(LocalDate date, String label, List<AgendaRow> rows) {

    private static final DateTimeFormatter LABEL = DateTimeFormatter.ofPattern("EEEE d/MM", Locale.of("es", "AR"));
    private static final DateTimeFormatter LABEL_WITH_YEAR =
            DateTimeFormatter.ofPattern("EEE d/MM/yyyy", Locale.of("es", "AR"));

    static AgendaDay of(LocalDate date, List<AgendaRow> rows) {
        return new AgendaDay(date, label(date), rows);
    }

    /** "martes 10/03": reception reads the day of the week before the date. */
    static String label(LocalDate date) {
        return LABEL.format(date);
    }

    /** "mar 10/03/2026", for lists that span years, such as a patient's appointments. */
    static String labelWithYear(LocalDate date) {
        return LABEL_WITH_YEAR.format(date);
    }
}
