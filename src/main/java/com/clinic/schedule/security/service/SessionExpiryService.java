package com.clinic.schedule.security.service;

import org.springframework.security.core.session.SessionInformation;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionalEventListener;

import com.clinic.schedule.user.api.UserDeactivated;

/**
 * Ends the open sessions of a user who was deactivated, so they lose access at once rather than
 * when their session runs out (D-68). A session ended here is refused on its next request, which is
 * sent to the login form.
 *
 * <p>Only once the deactivation commits: a refused one ends nothing. The API needs none of this —
 * it authenticates every request, and an inactive user is as good as unknown.
 *
 * <p>The sessions are known from Spring Security's registry, which lives in memory: the application
 * runs as one instance (D-66).
 */
@Service
class SessionExpiryService {

    private final SessionRegistry sessions;

    SessionExpiryService(SessionRegistry sessions) {
        this.sessions = sessions;
    }

    @TransactionalEventListener
    void on(UserDeactivated deactivated) {
        sessions.getAllPrincipals().stream()
                .filter(principal -> principal instanceof UserDetails user
                        && user.getUsername().equals(deactivated.username()))
                .flatMap(principal -> sessions.getAllSessions(principal, false).stream())
                .forEach(SessionInformation::expireNow);
    }
}
