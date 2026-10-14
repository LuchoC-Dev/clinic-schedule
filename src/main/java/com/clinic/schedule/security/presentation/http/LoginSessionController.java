package com.clinic.schedule.security.presentation.http;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.servlet.http.HttpServletRequest;

/**
 * What the single-page applications ask as they start: who is logged in, and the CSRF token (D-97). Logging
 * in and out are Spring Security's own, answered by {@link SessionHandlers}.
 */
@RestController
class LoginSessionController {

    private final SessionHandlers sessions;

    LoginSessionController(SessionHandlers sessions) {
        this.sessions = sessions;
    }

    @GetMapping("/api/session")
    StaffSessionResponse staff(HttpServletRequest request) {
        return sessions.staffSession(request, current());
    }

    @GetMapping("/api/portal/session")
    PatientSessionResponse patient(HttpServletRequest request) {
        return sessions.patientSession(request, current());
    }

    private static Authentication current() {
        return SecurityContextHolder.getContext().getAuthentication();
    }
}
