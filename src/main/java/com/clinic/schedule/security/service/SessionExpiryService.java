package com.clinic.schedule.security.service;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.security.core.session.SessionInformation;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionalEventListener;

import com.clinic.schedule.portal.api.PatientAccountDeactivated;
import com.clinic.schedule.user.api.UserDeactivated;

/**
 * Ends the open sessions of a user who was deactivated, so they lose access at once rather than
 * when their session runs out (D-68), and so of a patient's web account (D-79). A session ended here is
 * refused on its next request, which is sent to the login form.
 *
 * <p>Only once the deactivation commits: a refused one ends nothing. The API needs none of this —
 * it authenticates every request, and an inactive user is as good as unknown.
 *
 * <p>The sessions are known from Spring Security's registries, the staff's and the patients' (D-80), which
 * live in memory: the application runs as one instance (D-66).
 */
@Service
class SessionExpiryService {

    private final SessionRegistry staff;
    private final SessionRegistry patients;

    SessionExpiryService(@Qualifier("sessionRegistry") SessionRegistry staff,
            @Qualifier("patientSessionRegistry") SessionRegistry patients) {
        this.staff = staff;
        this.patients = patients;
    }

    @TransactionalEventListener
    void on(UserDeactivated deactivated) {
        expire(staff, deactivated.username());
    }

    /** A patient's principal is named by their account's id (D-80). */
    @TransactionalEventListener
    void on(PatientAccountDeactivated deactivated) {
        expire(patients, deactivated.accountId().toString());
    }

    private static void expire(SessionRegistry sessions, String name) {
        sessions.getAllPrincipals().stream()
                .filter(principal -> principal instanceof UserDetails user && user.getUsername().equals(name))
                .flatMap(principal -> sessions.getAllSessions(principal, false).stream())
                .forEach(SessionInformation::expireNow);
    }
}
