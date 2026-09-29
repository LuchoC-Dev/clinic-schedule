package com.clinic.schedule.security.presentation.screen;

import java.io.IOException;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.access.AccessDeniedHandlerImpl;
import org.springframework.stereotype.Component;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import com.clinic.schedule.security.presentation.ClinicalRouteRefusals;

/**
 * The screens' 403, as Spring Security gives it — the error page —, saying so first when the route
 * refused is a clinical one (D-72).
 */
@Component
public class ScreenAccessDeniedHandler implements AccessDeniedHandler {

    private final ClinicalRouteRefusals refusals;
    private final AccessDeniedHandler errorPage = new AccessDeniedHandlerImpl();

    ScreenAccessDeniedHandler(ClinicalRouteRefusals refusals) {
        this.refusals = refusals;
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response, AccessDeniedException e)
            throws IOException, ServletException {
        refusals.report(request, e);
        errorPage.handle(request, response, e);
    }
}
