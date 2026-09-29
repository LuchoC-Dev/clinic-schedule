package com.clinic.schedule.appointment.adapter.in.screen;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * Everything the booking screen holds, whether it is being refreshed or submitted.
 *
 * <p>The screen is a single form, so choosing a reason or searching a patient never loses the
 * time or the patient already chosen: a refresh sends it all and it all comes back (D-35).
 *
 * <p>The patient is either one already registered, {@code patientId}, or a new one typed in the
 * {@code newPatient…} fields, registered and booked at once (D-37, D-75). Those fields are personal
 * data, so the screen's address never carries them: they come back through {@link NewPatientForm}.
 */
record BookingScreenRequest(
        UUID practitionerId,
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
        UUID reasonId,
        String patientQuery,
        UUID patientId,
        String newPatientName,
        String newPatientDocumentType,
        String newPatientDocumentNumber,
        String newPatientPhoneNumber,
        String newPatientPhoneOwnerRelation,
        String newPatientPhoneOwnerName,
        String newPatientEmailAddress,
        String newPatientEmailOwnerRelation,
        String newPatientEmailOwnerName,
        @DateTimeFormat(iso = DateTimeFormat.ISO.TIME) LocalTime time,
        @DateTimeFormat(iso = DateTimeFormat.ISO.TIME) LocalTime otherTime,
        String note) {

    /** The time typed in "…u otra hora" wins over a suggested one. */
    LocalTime chosenTime() {
        return otherTime != null ? otherTime : time;
    }

    String cleanNote() {
        return blankToNull(note);
    }

    NewPatientForm newPatient() {
        return new NewPatientForm(blankToNull(newPatientName), blankToNull(newPatientDocumentType),
                blankToNull(newPatientDocumentNumber), blankToNull(newPatientPhoneNumber),
                blankToNull(newPatientPhoneOwnerRelation), blankToNull(newPatientPhoneOwnerName),
                blankToNull(newPatientEmailAddress), blankToNull(newPatientEmailOwnerRelation),
                blankToNull(newPatientEmailOwnerName));
    }

    boolean hasNewPatient() {
        return newPatient().given();
    }

    /** A patient picked from the search and a new one typed in: which one was meant is unclear. */
    boolean hasTwoPatients() {
        return patientId != null && hasNewPatient();
    }

    /** What is still to be chosen before booking, in Spanish, or null when nothing is. */
    String missing() {
        List<String> missing = new ArrayList<>();
        if (practitionerId == null) {
            missing.add("el profesional");
        }
        if (reasonId == null) {
            missing.add("la razón");
        }
        if (patientId == null && !hasNewPatient()) {
            missing.add("un paciente");
        }
        if (chosenTime() == null) {
            missing.add("un horario");
        }
        if (missing.isEmpty()) {
            return null;
        }
        String last = missing.removeLast();
        return "Elegí " + (missing.isEmpty() ? last : String.join(", ", missing) + " y " + last) + ".";
    }

    /**
     * The booking screen again, holding everything chosen so far but the new patient, whose data never
     * goes in an address (D-75).
     */
    String toScreenUrl() {
        UriComponentsBuilder url = UriComponentsBuilder.fromPath("/agenda/new");
        addIfPresent(url, "practitionerId", practitionerId);
        url.queryParam("date", date);
        addIfPresent(url, "reasonId", reasonId);
        addIfPresent(url, "patientQuery", blankToNull(patientQuery));
        addIfPresent(url, "patientId", patientId);
        addIfPresent(url, "time", time);
        addIfPresent(url, "otherTime", otherTime);
        addIfPresent(url, "note", cleanNote());
        return url.encode().toUriString();
    }

    private static void addIfPresent(UriComponentsBuilder url, String name, Object value) {
        if (value != null) {
            url.queryParam(name, value);
        }
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }
}
