package com.clinic.schedule.security.presentation.http;

import java.util.UUID;

import com.clinic.schedule.user.api.Role;

/**
 * Who of the staff is logged in to the staff's application, if anyone, and the CSRF token its writes carry
 * (D-97). The token is asked for before logging in too, since the login itself carries it.
 *
 * @param user null when nobody is logged in
 */
record StaffSessionResponse(User user, String csrfToken) {

    /**
     * @param practitionerId the practitioner the user stands for, or null
     * @param temporaryPassword the user must change their password before anything else (D-69)
     */
    record User(String username, Role role, UUID practitionerId, boolean temporaryPassword) {
    }
}
