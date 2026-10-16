package com.clinic.schedule.security.presentation;

import java.io.IOException;

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
 * <p>Anything but the change, and the staff's application asking who is logged in (D-97), is refused with a 403
 * that says why. It runs after the routes were authorized, so it only narrows what a user may already reach.
 */
public class TemporaryPasswordFilter extends OncePerRequestFilter {

    private final ApiSecurityErrorHandler errors;

    public TemporaryPasswordFilter(ApiSecurityErrorHandler errors) {
        this.errors = errors;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (!hasTemporaryPassword() || isOpen(request)) {
            chain.doFilter(request, response);
        } else {
            errors.passwordMustBeChanged(response);
        }
    }

    private static boolean isOpen(HttpServletRequest request) {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        return path.equals("/api/password") && "POST".equals(request.getMethod())
                || path.equals("/api/session") && "GET".equals(request.getMethod());
    }

    private static boolean hasTemporaryPassword() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null && authentication.getAuthorities().stream()
                .anyMatch(authority -> AppUserDetails.TEMPORARY_PASSWORD.equals(authority.getAuthority()));
    }
}
