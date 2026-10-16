package com.clinic.schedule;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.web.context.support.WebApplicationContextUtils;

import com.clinic.schedule.security.service.AppUserDetailsService;
import com.clinic.schedule.security.service.PatientAccountDetailsService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * How a test is someone: the API's only way in is a session's login (D-97, D-115).
 *
 * <ul>
 *   <li>{@link #staffUser(String)} and {@link #patientUser(String)}: the user or the patient as the application knows them —
 *       their role, their practitioner, their account —, without logging in, for what a route or a use case decides
 *       of them;</li>
 *   <li>{@link #staff} and {@link #patient}: a real login with a password, for what the login itself decides — a
 *       wrong or temporary password, a deactivated user, a locked account —, and the browser's session it opens.</li>
 * </ul>
 */
public final class Logins {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String CSRF = "X-CSRF-TOKEN";

    private Logins() {
    }

    /** The staff's user of that username, as the application loads them. */
    public static RequestPostProcessor staffUser(String username) {
        return request -> user(bean(request, AppUserDetailsService.class).loadUserByUsername(username))
                .postProcessRequest(request);
    }

    /** The patient with a web account of that document, such as "DNI 30111222", as the application loads them. */
    public static RequestPostProcessor patientUser(String document) {
        return request -> user(bean(request, PatientAccountDetailsService.class).loadUserByUsername(document))
                .postProcessRequest(request);
    }

    private static <T> T bean(MockHttpServletRequest request, Class<T> type) {
        return WebApplicationContextUtils.getRequiredWebApplicationContext(request.getServletContext()).getBean(type);
    }

    /**
     * A browser's session, and the CSRF token its application holds in memory and sends with every write. Each
     * request carries the session's cookie, as a browser's does.
     */
    public record Session(MockHttpSession session, String token) implements RequestPostProcessor {

        @Override
        public MockHttpServletRequest postProcessRequest(MockHttpServletRequest request) {
            request.setSession(session);
            request.setRequestedSessionId(session.getId());
            request.setRequestedSessionIdFromCookie(true);
            request.setRequestedSessionIdValid(!session.isInvalid());
            if (token != null) {
                request.addHeader(CSRF, token);
            }
            return request;
        }
    }

    /** The staff's login, as their application sends it: the token asked for first, then the form. */
    public static ResultActions staffLogIn(MockMvc mockMvc, String username, String password) throws Exception {
        Session opened = open(mockMvc, "/api/session");
        return mockMvc.perform(post("/api/session").with(opened)
                .param("username", username).param("password", password));
    }

    /** Logged in to the staff's application. */
    public static Session staff(MockMvc mockMvc, String username, String password) throws Exception {
        return loggedIn(staffLogIn(mockMvc, username, password));
    }

    /** A patient's login, with their document as "DNI 30111222": its type and its number. */
    public static ResultActions patientLogIn(MockMvc mockMvc, String document, String password) throws Exception {
        Session opened = open(mockMvc, "/api/portal/session");
        String[] typeAndNumber = document.split(" ", 2);
        return mockMvc.perform(post("/api/portal/session").with(opened)
                .param("documentType", typeAndNumber[0])
                .param("documentNumber", typeAndNumber.length > 1 ? typeAndNumber[1] : "")
                .param("password", password));
    }

    /** Logged in to the patients' application. */
    public static Session patient(MockMvc mockMvc, String document, String password) throws Exception {
        return loggedIn(patientLogIn(mockMvc, document, password));
    }

    /** The application starting: it asks who is logged in, and gets its token. */
    private static Session open(MockMvc mockMvc, String sessionPath) throws Exception {
        MockHttpSession session = new MockHttpSession();
        return new Session(session, tokenOf(mockMvc.perform(get(sessionPath).with(new Session(session, null)))));
    }

    private static Session loggedIn(ResultActions login) throws Exception {
        login.andExpect(status().isOk());
        MockHttpSession session = (MockHttpSession) login.andReturn().getRequest().getSession(false);
        return new Session(session, tokenOf(login));
    }

    private static String tokenOf(ResultActions answer) throws Exception {
        JsonNode body = JSON.readTree(answer.andReturn().getResponse().getContentAsString());
        return body.get("csrfToken").asText();
    }
}
