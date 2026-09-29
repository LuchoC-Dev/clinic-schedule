package com.clinic.schedule.security.presentation.http;

import java.io.IOException;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import com.clinic.schedule.shared.http.ErrorResponse;
import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * 401 and 403 for the API, with the same error body as every other error (D-23). Both are
 * produced by the filter chain before any controller advice runs, so they are written here.
 *
 * <p>The 401 carries no {@code WWW-Authenticate} header, so a browser does not pop up its own
 * credentials dialog over the application.
 */
@Component
public class ApiSecurityErrorHandler implements AuthenticationEntryPoint, AccessDeniedHandler {

    private final ObjectMapper json;

    ApiSecurityErrorHandler(ObjectMapper json) {
        this.json = json;
    }

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response, AuthenticationException e)
            throws IOException {
        write(response, HttpStatus.UNAUTHORIZED, ErrorResponse.of(401, "Unauthenticated", "Valid credentials are required"));
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response, AccessDeniedException e)
            throws IOException {
        write(response, HttpStatus.FORBIDDEN, ErrorResponse.of(403, "Forbidden", "You are not allowed to do this"));
    }

    /** A user whose password is temporary, asking for anything but changing it (D-69). */
    public void passwordMustBeChanged(HttpServletResponse response) throws IOException {
        write(response, HttpStatus.FORBIDDEN,
                ErrorResponse.of(403, "Forbidden", "The password must be changed first, through POST /api/password"));
    }

    private void write(HttpServletResponse response, HttpStatus status, ErrorResponse body) throws IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        json.writeValue(response.getOutputStream(), body);
    }
}
