package com.clinic.schedule.portal.adapter.in.http;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import com.clinic.schedule.portal.domain.FamilyMember;

/** The bodies of registering on the web (D-91), with children under 13 (D-92). */
final class RegistrationRequests {

    private RegistrationRequests() {
    }

    /** An adult registering themselves: the phone may be left out, and so may the children. */
    record Registration(String fullName, String documentType, String documentNumber, LocalDate birthDate,
            String phone, String email, String password, List<Child> children) {
    }

    /** A child under 13, and what the adult is to them. */
    record Child(String fullName, String documentType, String documentNumber, LocalDate birthDate,
            FamilyMember.Relation relation) {
    }

    /** A child registered later from the account, into a group of the adult's, or a new one when left out. */
    record AccountChild(String fullName, String documentType, String documentNumber, LocalDate birthDate,
            FamilyMember.Relation relation, UUID groupId) {
    }

    /** The children a confirmation could not register, by name: they go to reception. */
    record Confirmed(List<String> notRegistered) {
    }

    /** The token of the email's link. */
    record Confirmation(String token) {
    }
}
