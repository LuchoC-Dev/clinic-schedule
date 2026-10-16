package com.clinic.schedule.security.presentation.http;

import java.io.IOException;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.security.web.authentication.logout.LogoutSuccessHandler;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.session.SessionInformationExpiredStrategy;
import org.springframework.stereotype.Component;

import com.clinic.schedule.patient.api.PatientReader;
import com.clinic.schedule.security.service.AppUserDetails;
import com.clinic.schedule.security.service.PatientAccountDetails;
import com.clinic.schedule.shared.http.ErrorResponse;
import com.clinic.schedule.user.api.Role;
import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * The single-page applications' session, answered in JSON (D-97): logging in, failing to, logging out, and a
 * session ended by a deactivation or a new password (D-68, D-79, D-86). Spring Security's form login does the
 * rest — a new session id, a new CSRF token, the session registered.
 *
 * <p>A login answers what {@code GET /api/session} would, with the CSRF token the login just renewed. A failed
 * one says the same whatever failed: for a patient, saying that the account is locked would tell that the
 * document has one (D-86).
 */
@Component
public class SessionHandlers {

    private final ObjectMapper json;
    private final PatientReader patients;

    SessionHandlers(ObjectMapper json, PatientReader patients) {
        this.json = json;
        this.patients = patients;
    }

    /** The waiting room's session does not run out while nobody touches it: nobody does, it is on the wall (D-70). */
    public AuthenticationSuccessHandler staffLoggedIn() {
        return (request, response, authentication) -> {
            if (authentication.getPrincipal() instanceof AppUserDetails user && user.role() == Role.WAITING_ROOM) {
                request.getSession().setMaxInactiveInterval(-1); // never, for the servlet container
            }
            write(response, HttpStatus.OK, staffSession(request, authentication));
        };
    }

    public AuthenticationFailureHandler staffLoginFailed() {
        return (request, response, e) -> write(response, HttpStatus.UNAUTHORIZED,
                ErrorResponse.of(401, "Unauthenticated", "Wrong username or password"));
    }

    public AuthenticationSuccessHandler patientLoggedIn() {
        return (request, response, authentication) ->
                write(response, HttpStatus.OK, patientSession(request, authentication));
    }

    public AuthenticationFailureHandler patientLoginFailed() {
        return (request, response, e) -> write(response, HttpStatus.UNAUTHORIZED, ErrorResponse.of(401,
                "Unauthenticated", "Wrong document or password, or the account is locked for a while"));
    }

    /** Logging out ends the whole session, the other side's login in it included (D-80). */
    public LogoutSuccessHandler loggedOut() {
        return (request, response, authentication) -> response.setStatus(HttpStatus.NO_CONTENT.value());
    }

    /** A session ended by a deactivation or a new password, on its next request. */
    public SessionInformationExpiredStrategy sessionEnded() {
        return event -> write(event.getResponse(), HttpStatus.UNAUTHORIZED,
                ErrorResponse.of(401, "Unauthenticated", "The session was ended"));
    }

    StaffSessionResponse staffSession(HttpServletRequest request, Authentication authentication) {
        StaffSessionResponse.User user = authentication != null
                && authentication.getPrincipal() instanceof AppUserDetails details
                ? new StaffSessionResponse.User(details.getUsername(), details.role(), details.practitionerId(),
                        details.hasTemporaryPassword())
                : null;
        return new StaffSessionResponse(user, csrfToken(request));
    }

    PatientSessionResponse patientSession(HttpServletRequest request, Authentication authentication) {
        boolean loggedIn = authentication != null && authentication.getPrincipal() instanceof PatientAccountDetails;
        return new PatientSessionResponse(loggedIn, csrfToken(request), patients.documentTypes());
    }

    /** The token as this request holds it: after a login, the one the login renewed. */
    private static String csrfToken(HttpServletRequest request) {
        CsrfToken token = (CsrfToken) request.getAttribute(CsrfToken.class.getName());
        return token == null ? null : token.getToken();
    }

    private void write(HttpServletResponse response, HttpStatus status, Object body) throws IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        json.writeValue(response.getOutputStream(), body);
    }
}
