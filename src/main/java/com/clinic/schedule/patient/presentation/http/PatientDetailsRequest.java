package com.clinic.schedule.patient.presentation.http;

import java.time.LocalDate;

import com.clinic.schedule.patient.entity.Email;
import com.clinic.schedule.patient.entity.HealthInsurance;
import com.clinic.schedule.patient.entity.PatientDetails;
import com.clinic.schedule.patient.entity.Phone;
import com.clinic.schedule.patient.entity.Relation;
import com.clinic.schedule.patient.entity.Responsible;

/**
 * The body for changing what is known of a patient besides who they are (D-73, D-74). Every part is
 * optional, and one left out is cleared: the body says the details as they are to be.
 *
 * <p>The phone and the email each carry their owner: null when they are the patient's own, or the
 * name and relation of the person they belong to. The insurance is the insurer's name, the member
 * number and the plan. The formats are checked by the entity, so the API and the screens refuse the
 * same things (D-38).
 */
record PatientDetailsRequest(LocalDate birthDate, PhoneRequest phone, EmailRequest email,
        InsuranceRequest insurance) {

    record PhoneRequest(String number, OwnerRequest owner) {
    }

    record EmailRequest(String address, OwnerRequest owner) {
    }

    record OwnerRequest(String name, Relation relation) {

        static Responsible of(OwnerRequest owner) {
            return owner == null ? null : Responsible.ofNullable(owner.name(), owner.relation());
        }
    }

    record InsuranceRequest(String insurer, String memberNumber, String plan) {
    }

    PatientDetails toDetails() {
        return new PatientDetails(birthDate,
                phone == null ? null : Phone.ofNullable(phone.number(), OwnerRequest.of(phone.owner())),
                email == null ? null : Email.ofNullable(email.address(), OwnerRequest.of(email.owner())),
                insurance == null ? null
                        : HealthInsurance.ofNullable(insurance.insurer(), insurance.memberNumber(), insurance.plan()));
    }
}
