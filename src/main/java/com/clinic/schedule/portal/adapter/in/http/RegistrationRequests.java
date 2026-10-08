package com.clinic.schedule.portal.adapter.in.http;

import java.time.LocalDate;

/** The bodies of registering on the web (D-91). */
final class RegistrationRequests {

    private RegistrationRequests() {
    }

    /** An adult registering themselves: the phone may be left out. */
    record Registration(String fullName, String documentType, String documentNumber, LocalDate birthDate,
            String phone, String email, String password) {
    }

    /** The token of the email's link. */
    record Confirmation(String token) {
    }
}
