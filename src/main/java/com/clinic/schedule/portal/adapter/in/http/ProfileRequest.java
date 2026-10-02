package com.clinic.schedule.portal.adapter.in.http;

import com.clinic.schedule.portal.domain.OwnContact;

/**
 * The body a patient sends to change their contact and health insurance (D-81), shaped as reception's
 * (D-73): every part optional, and one left out is cleared. An owner is a name and a relation such as
 * "MOTHER", left out when the phone or the email is the patient's own.
 */
record ProfileRequest(Phone phone, Email email, Insurance insurance) {

    record Phone(String number, Owner owner) {
    }

    record Email(String address, Owner owner) {
    }

    record Owner(String name, String relation) {
    }

    record Insurance(String insurer, String memberNumber, String plan) {
    }

    OwnContact toContact() {
        Owner phoneOwner = phone == null ? null : phone.owner();
        Owner emailOwner = email == null ? null : email.owner();
        return new OwnContact(phone == null ? null : phone.number(), phoneOwner == null ? null : phoneOwner.name(),
                phoneOwner == null ? null : phoneOwner.relation(), email == null ? null : email.address(),
                emailOwner == null ? null : emailOwner.name(), emailOwner == null ? null : emailOwner.relation(),
                insurance == null ? null : insurance.insurer(), insurance == null ? null : insurance.memberNumber(),
                insurance == null ? null : insurance.plan());
    }
}
