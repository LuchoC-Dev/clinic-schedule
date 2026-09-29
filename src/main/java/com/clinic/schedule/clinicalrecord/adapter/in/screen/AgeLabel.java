package com.clinic.schedule.clinicalrecord.adapter.in.screen;

import java.time.Period;

/**
 * A patient's age as a practitioner reads it beside the record (D-73): in years, and in months below
 * two, as paediatrics counts it — in days for a baby not a month old.
 */
final class AgeLabel {

    private AgeLabel() {
    }

    /** "45 años", "18 meses", "1 mes", "12 días"; null for no age. */
    static String of(Period age) {
        if (age == null) {
            return null;
        }
        if (age.getYears() >= 2) {
            return age.getYears() + " años";
        }
        long months = age.toTotalMonths();
        if (months >= 1) {
            return months == 1 ? "1 mes" : months + " meses";
        }
        return age.getDays() == 1 ? "1 día" : age.getDays() + " días";
    }
}
