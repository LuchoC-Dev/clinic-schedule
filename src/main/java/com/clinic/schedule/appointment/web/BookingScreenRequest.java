package com.clinic.schedule.appointment.web;

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
 * {@code newPatient…} fields, registered when booking (D-37).
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

    boolean hasNewPatient() {
        return blankToNull(newPatientName) != null
                || blankToNull(newPatientDocumentType) != null
                || blankToNull(newPatientDocumentNumber) != null;
    }

    /** A patient picked from the search and a new one typed in: which one was meant is unclear. */
    boolean hasTwoPatients() {
        return patientId != null && hasNewPatient();
    }

    /** The same screen once the new patient is registered: now they are simply the chosen one. */
    BookingScreenRequest withPatient(UUID registered) {
        return new BookingScreenRequest(practitionerId, date, reasonId, patientQuery, registered,
                null, null, null, time, otherTime, note);
    }

    /** What is still to be chosen before booking, in Spanish, or null when nothing is. */
    String missing() {
        List<String> missing = new ArrayList<>();
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

    /** The booking screen again, holding everything chosen so far. */
    String toScreenUrl() {
        UriComponentsBuilder url = UriComponentsBuilder.fromPath("/agenda/new")
                .queryParam("practitionerId", practitionerId)
                .queryParam("date", date);
        addIfPresent(url, "reasonId", reasonId);
        addIfPresent(url, "patientQuery", blankToNull(patientQuery));
        addIfPresent(url, "patientId", patientId);
        addIfPresent(url, "newPatientName", blankToNull(newPatientName));
        addIfPresent(url, "newPatientDocumentType", blankToNull(newPatientDocumentType));
        addIfPresent(url, "newPatientDocumentNumber", blankToNull(newPatientDocumentNumber));
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
