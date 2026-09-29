package com.clinic.schedule.appointment.adapter.in.screen;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import com.clinic.schedule.appointment.application.port.PatientRegistrationPort;

/**
 * A new patient as the booking screen holds them while reception chooses the day and the time: their
 * name, maybe their document, and maybe a phone and an email, each saying whose it is (D-37, D-75).
 *
 * <p>It is personal data, so it never travels in the screen's address, where the browser's history
 * would keep it: between one step and the next it rides in a flash attribute, in the session (D-75).
 */
record NewPatientForm(String name, String documentType, String documentNumber,
        String phoneNumber, String phoneOwnerRelation, String phoneOwnerName,
        String emailAddress, String emailOwnerRelation, String emailOwnerName) {

    static final NewPatientForm NONE = new NewPatientForm(null, null, null, null, null, null, null, null, null);

    /** Whether anything of a new patient was typed. */
    boolean given() {
        return Stream.of(name, documentType, documentNumber, phoneNumber, phoneOwnerRelation, phoneOwnerName,
                emailAddress, emailOwnerRelation, emailOwnerName).anyMatch(value -> value != null && !value.isBlank());
    }

    PatientRegistrationPort.NewPatient toPatient() {
        return new PatientRegistrationPort.NewPatient(name, documentType, documentNumber,
                phoneNumber, phoneOwnerRelation, phoneOwnerName, emailAddress, emailOwnerRelation, emailOwnerName);
    }

    /** The relations the patient module offers, each with its name in Spanish, in their order. */
    static Map<String, String> relations(List<String> offered) {
        Map<String, String> relations = new LinkedHashMap<>();
        for (String relation : offered) {
            relations.put(relation, switch (relation) {
                case "MOTHER" -> "Madre";
                case "FATHER" -> "Padre";
                case "GUARDIAN" -> "Tutor/a";
                case "PARTNER" -> "Pareja";
                case "CHILD" -> "Hijo/a";
                default -> "Otra persona";
            });
        }
        return relations;
    }
}
