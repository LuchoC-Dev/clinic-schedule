package com.clinic.schedule.security.presentation.http;

import java.io.IOException;

import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;

/**
 * The patients' login sends the document as a type, chosen from a list, and a number (D-80, D-97). Spring
 * Security's form login reads a single username, so this joins the two the way the patient module reads a
 * document — "DNI 30111222" — before the login reads it, on the login's own request alone.
 */
public class DocumentLoginFilter extends OncePerRequestFilter {

    private final String loginPath;

    public DocumentLoginFilter(String loginPath) {
        this.loginPath = loginPath;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        return !"POST".equals(request.getMethod()) || !loginPath.equals(path);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String type = request.getParameter("documentType");
        String number = request.getParameter("documentNumber");
        String document = type == null || number == null ? "" : type.strip() + " " + number.strip();
        chain.doFilter(new HttpServletRequestWrapper(request) {
            @Override
            public String getParameter(String name) {
                return "username".equals(name) ? document : super.getParameter(name);
            }
        }, response);
    }
}
