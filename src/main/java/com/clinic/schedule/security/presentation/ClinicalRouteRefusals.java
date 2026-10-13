package com.clinic.schedule.security.presentation;

import java.util.List;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.csrf.CsrfException;
import org.springframework.stereotype.Component;

import jakarta.servlet.http.HttpServletRequest;

import com.clinic.schedule.security.api.ClinicalRouteRefused;

/**
 * Says so when a clinical route is refused to a logged-in user by their role (D-72), on the API and
 * on the screens alike. The clinical routes are those of the clinical record — records, files,
 * consultations, checks, orders —; the practitioner's queue is theirs, but holds no clinical content.
 *
 * <p>A form without its CSRF token is refused too, but for the form, not for who sent it, so it is not
 * said. A request with nobody logged in never gets here: Spring Security sends it to log in instead.
 */
@Component
public class ClinicalRouteRefusals {

    private static final List<String> CLINICAL = List.of("/consultations", "/checks", "/orders", "/attachments",
            "/clinical-records", "/record-copies");

    private final ApplicationEventPublisher events;

    ClinicalRouteRefusals(ApplicationEventPublisher events) {
        this.events = events;
    }

    public void report(HttpServletRequest request, AccessDeniedException refusal) {
        Authentication user = SecurityContextHolder.getContext().getAuthentication();
        if (refusal instanceof CsrfException || user == null) {
            return;
        }
        String path = request.getRequestURI().substring(request.getContextPath().length());
        if (isClinical(path)) {
            events.publishEvent(new ClinicalRouteRefused(user.getName(), request.getMethod(), path));
        }
    }

    static boolean isClinical(String path) {
        String route = path.startsWith("/api/") ? path.substring("/api".length()) : path;
        return CLINICAL.stream().anyMatch(prefix -> route.equals(prefix) || route.startsWith(prefix + "/"));
    }
}
