package com.clinic.schedule.patient.presentation.screen;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

import com.clinic.schedule.patient.entity.Consent;
import com.clinic.schedule.patient.entity.HealthInsurance;
import com.clinic.schedule.patient.entity.PatientDetailsChange;
import com.clinic.schedule.patient.entity.Responsible;
import com.clinic.schedule.security.api.PatientUsername;

/**
 * A change to one of a patient's details as their screen lists it: when, who, which detail, and what
 * it was before and after, in Spanish (D-73). A change the patient made from their web account says so,
 * rather than naming the account (D-81).
 */
record DetailsChangeEntry(String when, String by, String field, String before, String after) {

    private static final DateTimeFormatter WHEN =
            DateTimeFormatter.ofPattern("EEE dd/MM/yyyy HH:mm", Locale.forLanguageTag("es-AR"));

    static DetailsChangeEntry of(PatientDetailsChange change) {
        if (change.getField() == PatientDetailsChange.Field.INSURANCE) {
            return new DetailsChangeEntry(WHEN.format(change.getChangedAt()), by(change.getChangedBy()),
                    field(change.getField()), insurance(change.getInsuranceBefore()),
                    insurance(change.getInsuranceAfter()));
        }
        return new DetailsChangeEntry(WHEN.format(change.getChangedAt()), by(change.getChangedBy()),
                field(change.getField()),
                describe(change.getField(), change.getValueBefore(), change.getOwnerBefore()),
                describe(change.getField(), change.getValueAfter(), change.getOwnerAfter()));
    }

    private static String by(String username) {
        return PatientUsername.is(username) ? "el paciente (web)" : username;
    }

    private static String field(PatientDetailsChange.Field field) {
        return switch (field) {
            case BIRTH_DATE -> "Fecha de nacimiento";
            case PHONE -> "Teléfono";
            case EMAIL -> "Email";
            case INSURANCE -> "Obra social";
            case PHONE_CONSENT -> "Recordatorios al teléfono";
            case EMAIL_CONSENT -> "Recordatorios al email";
        };
    }

    private static String insurance(HealthInsurance insurance) {
        return insurance == null ? "—" : DetailsEntry.describe(insurance);
    }

    private static String describe(PatientDetailsChange.Field field, String value, Responsible owner) {
        if (value == null) {
            return "—";
        }
        if (field == PatientDetailsChange.Field.BIRTH_DATE) {
            return DetailsEntry.day(LocalDate.parse(value));
        }
        if (field == PatientDetailsChange.Field.PHONE_CONSENT || field == PatientDetailsChange.Field.EMAIL_CONSENT) {
            return ConsentEntry.describe(Consent.valueOf(value));
        }
        return DetailsEntry.describe(value, owner);
    }
}
