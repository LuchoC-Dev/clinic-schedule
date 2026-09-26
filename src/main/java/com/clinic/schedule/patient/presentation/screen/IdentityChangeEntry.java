package com.clinic.schedule.patient.presentation.screen;

import java.time.format.DateTimeFormatter;
import java.util.Locale;

import com.clinic.schedule.patient.entity.IdentityDocument;
import com.clinic.schedule.patient.entity.IdentityStatus;
import com.clinic.schedule.patient.entity.PatientIdentityChange;

/** A change to a patient's identity as their screen lists it: when, who, and what it was before and after (D-60). */
record IdentityChangeEntry(String when, String by, String before, String after) {

    private static final DateTimeFormatter WHEN =
            DateTimeFormatter.ofPattern("EEE dd/MM/yyyy HH:mm", Locale.forLanguageTag("es-AR"));

    static IdentityChangeEntry of(PatientIdentityChange change) {
        return new IdentityChangeEntry(WHEN.format(change.getChangedAt()), change.getChangedBy(),
                describe(change.getFullNameBefore(), change.getDocumentBefore(), change.getStatusBefore()),
                describe(change.getFullNameAfter(), change.getDocumentAfter(), change.getStatusAfter()));
    }

    private static String describe(String fullName, IdentityDocument document, IdentityStatus status) {
        String label = PatientEntry.label(document);
        return fullName + (label == null ? ", sin documento" : ", " + label) + " — " + PatientEntry.identity(status);
    }
}
