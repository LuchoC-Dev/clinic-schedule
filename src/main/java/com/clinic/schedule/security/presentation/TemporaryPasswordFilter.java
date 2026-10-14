package com.clinic.schedule.security.presentation;

import java.io.IOException;
import java.util.Set;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import com.clinic.schedule.security.presentation.http.ApiSecurityErrorHandler;
import com.clinic.schedule.security.service.AppUserDetails;

/**
 * Keeps a user whose password is temporary on the password change until they make it (D-69): the
 * password was set by an admin, so until it is changed, the admin knows it too.
 *
 * <p>On the screens, anything but the change, the login and logging out is sent to the change. On
 * the API, anything but the change, and the staff's application asking who is logged in (D-97), is refused
 * with a 403 that says why. It runs after the routes
 * were authorized, so it only narrows what a user may already reach.
 */
public class TemporaryPasswordFilter extends OncePerRequestFilter {

    private static final Set<String> SCREENS_OPEN = Set.of("/password", "/login", "/logout", "/error");

    private final ApiSecurityErrorHandler api;

    private TemporaryPasswordFilter(ApiSecurityErrorHandler api) {
        this.api = api;
    }

    /** Sends the user to the password screen. */
    public static TemporaryPasswordFilter forScreens() {
        return new TemporaryPasswordFilter(null);
    }

    /** Refuses with a 403 anything but changing the password. */
    public static TemporaryPasswordFilter forApi(ApiSecurityErrorHandler errors) {
        return new TemporaryPasswordFilter(errors);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (!hasTemporaryPassword() || isOpen(request)) {
            chain.doFilter(request, response);
        } else if (api != null) {
            api.passwordMustBeChanged(response);
        } else {
            response.sendRedirect(request.getContextPath() + "/password?required");
        }
    }

    private boolean isOpen(HttpServletRequest request) {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        return api != null
                ? path.equals("/api/password") && "POST".equals(request.getMethod())
                        || path.equals("/api/session") && "GET".equals(request.getMethod())
                : SCREENS_OPEN.contains(path);
    }

    private static boolean hasTemporaryPassword() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null && authentication.getAuthorities().stream()
                .anyMatch(authority -> AppUserDetails.TEMPORARY_PASSWORD.equals(authority.getAuthority()));
    }
}
