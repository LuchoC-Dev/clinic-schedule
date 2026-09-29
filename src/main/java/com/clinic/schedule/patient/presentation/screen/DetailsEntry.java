package com.clinic.schedule.patient.presentation.screen;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;

import com.clinic.schedule.patient.entity.Email;
import com.clinic.schedule.patient.entity.HealthInsurance;
import com.clinic.schedule.patient.entity.PatientDetails;
import com.clinic.schedule.patient.entity.Phone;
import com.clinic.schedule.patient.entity.Relation;
import com.clinic.schedule.patient.entity.Responsible;

/**
 * A patient's details as their screen shows them and fills its form with (D-73, D-74): each field null
 * when not given, and the relations in Spanish.
 */
record DetailsEntry(LocalDate birthDate, String phoneNumber, Relation phoneOwnerRelation, String phoneOwnerName,
        String emailAddress, Relation emailOwnerRelation, String emailOwnerName,
        String insurer, String memberNumber, String plan) {

    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    static DetailsEntry of(PatientDetails details) {
        Phone phone = details.phone();
        Email email = details.email();
        Responsible phoneOwner = phone == null ? null : phone.owner();
        Responsible emailOwner = email == null ? null : email.owner();
        HealthInsurance insurance = details.insurance();
        return new DetailsEntry(details.birthDate(),
                phone == null ? null : phone.number(),
                phoneOwner == null ? null : phoneOwner.relation(),
                phoneOwner == null ? null : phoneOwner.name(),
                email == null ? null : email.address(),
                emailOwner == null ? null : emailOwner.relation(),
                emailOwner == null ? null : emailOwner.name(),
                insurance == null ? null : insurance.insurer(),
                insurance == null ? null : insurance.memberNumber(),
                insurance == null ? null : insurance.plan());
    }

    /** An insurance as the screens read it, such as "OSDE · plan 310 · afiliado 61234567801". */
    static String describe(HealthInsurance insurance) {
        return insurance.insurer()
                + (insurance.plan() == null ? "" : " · plan " + insurance.plan())
                + (insurance.memberNumber() == null ? "" : " · afiliado " + insurance.memberNumber());
    }

    /** The relations the forms offer, in their order, each with its name in Spanish. */
    static Map<Relation, String> relations() {
        Map<Relation, String> relations = new LinkedHashMap<>();
        for (Relation relation : Relation.values()) {
            relations.put(relation, relation(relation));
        }
        return relations;
    }

    static String day(LocalDate date) {
        return DAY.format(date);
    }

    static String relation(Relation relation) {
        return switch (relation) {
            case MOTHER -> "Madre";
            case FATHER -> "Padre";
            case GUARDIAN -> "Tutor/a";
            case PARTNER -> "Pareja";
            case CHILD -> "Hijo/a";
            case OTHER -> "Otra persona";
        };
    }

    /** A phone or an email with whose it is, such as "1145556666 (Madre: Ana Pérez)", or just the value. */
    static String describe(String value, Responsible owner) {
        return owner == null ? value : value + " (" + relation(owner.relation()) + ": " + owner.name() + ")";
    }
}
