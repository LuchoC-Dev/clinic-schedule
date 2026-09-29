package com.clinic.schedule.patient.presentation.screen;

import java.time.LocalDate;

import org.springframework.format.annotation.DateTimeFormat;

import com.clinic.schedule.patient.entity.Email;
import com.clinic.schedule.patient.entity.HealthInsurance;
import com.clinic.schedule.patient.entity.PatientDetails;
import com.clinic.schedule.patient.entity.Phone;
import com.clinic.schedule.patient.entity.Relation;
import com.clinic.schedule.patient.entity.Responsible;

/**
 * The patient's details as their forms send them (D-73, D-74): each field empty when not given, and
 * the owner of the phone and of the email empty when they are the patient's own. What the fields hold
 * is checked by the entity, as it is for the API.
 */
record DetailsForm(
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate birthDate,
        String phoneNumber, Relation phoneOwnerRelation, String phoneOwnerName,
        String emailAddress, Relation emailOwnerRelation, String emailOwnerName,
        String insurer, String memberNumber, String plan) {

    PatientDetails toDetails() {
        return new PatientDetails(birthDate,
                Phone.ofNullable(phoneNumber, Responsible.ofNullable(phoneOwnerName, phoneOwnerRelation)),
                Email.ofNullable(emailAddress, Responsible.ofNullable(emailOwnerName, emailOwnerRelation)),
                HealthInsurance.ofNullable(insurer, memberNumber, plan));
    }
}
