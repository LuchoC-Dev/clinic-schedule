package com.clinic.schedule.security;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static com.clinic.schedule.Logins.staffUser;
import static com.clinic.schedule.portal.PatientAccountFixture.PASSWORD;
import static com.clinic.schedule.portal.PatientAccountFixture.RECEPTION;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import com.clinic.schedule.portal.KeptAccountMail;
import com.clinic.schedule.portal.PatientAccountFixture;
import com.clinic.schedule.portal.PatientAccountFixture.Patient;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * How the single-page applications log in (D-97): a session, as the screens', with the CSRF token they ask for
 * at {@code /api/session} and {@code /api/portal/session}, the token sent back in a header as a browser's
 * application would. Basic authentication still answers any other client, with no session and no token.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(KeptAccountMail.class)
class ApplicationSessionIT {

    private static final String CSRF = "X-CSRF-TOKEN";
    private static final String ADMIN_PASSWORD = "admin123";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper json;

    @Autowired
    private JdbcTemplate jdbc;

    private PatientAccountFixture accounts;

    @BeforeEach
    void setUp() {
        accounts = new PatientAccountFixture(mockMvc, json);
    }

    /**
     * A browser's session, and the CSRF token its application holds in memory. Each request carries the session's
     * cookie, as a browser's does — MockMvc alone hands the request its session without saying it was asked for.
     */
    private record Browser(MockHttpSession session, String token) implements RequestPostProcessor {

        @Override
        public MockHttpServletRequest postProcessRequest(MockHttpServletRequest request) {
            request.setSession(session);
            request.setRequestedSessionId(session.getId());
            request.setRequestedSessionIdFromCookie(true);
            request.setRequestedSessionIdValid(!session.isInvalid());
            return request;
        }
    }

    private String tokenOf(ResultActions answer) throws Exception {
        return json.readTree(answer.andReturn().getResponse().getContentAsString()).get("csrfToken").asText();
    }

    /** The application starting: it asks who is logged in, and gets its token. */
    private Browser open(String sessionPath) throws Exception {
        Browser browser = new Browser(new MockHttpSession(), null);
        return new Browser(browser.session(), tokenOf(mockMvc.perform(get(sessionPath).with(browser))));
    }

    private ResultActions staffLogIn(Browser browser, String username, String password) throws Exception {
        return mockMvc.perform(post("/api/session").with(browser).header(CSRF, browser.token())
                .param("username", username).param("password", password));
    }

    /** Logged in to the staff's application; the login renews the token, which it answers. */
    private Browser staff(String username, String password) throws Exception {
        Browser browser = open("/api/session");
        String token = tokenOf(staffLogIn(browser, username, password).andExpect(status().isOk()));
        return new Browser(browser.session(), token);
    }

    private ResultActions patientLogIn(Browser browser, Patient patient, String password) throws Exception {
        return mockMvc.perform(post("/api/portal/session").with(browser).header(CSRF, browser.token())
                .param("documentType", "DNI").param("documentNumber", patient.dni()).param("password", password));
    }

    private Browser patient(Patient patient) throws Exception {
        Browser browser = open("/api/portal/session");
        String token = tokenOf(patientLogIn(browser, patient, PASSWORD).andExpect(status().isOk()));
        return new Browser(browser.session(), token);
    }

    /** A new user of the role, with a password of their own already, or still the admin's when temporary. */
    private String userId(String username, String password, boolean temporary) throws Exception {
        String chosen = temporary ? password : "temporal-" + password;
        String id = json.readTree(mockMvc.perform(post("/api/users").with(staffUser("admin"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username": "%s", "role": "RECEPTION", "practitionerId": null, "password": "%s"}
                                """.formatted(username, chosen)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString()).get("id").asText();
        if (!temporary) {
            Browser browser = staff(username, chosen);
            mockMvc.perform(post("/api/password").with(browser).header(CSRF, browser.token())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"currentPassword": "%s", "newPassword": "%s"}
                                    """.formatted(chosen, password)))
                    .andExpect(status().isNoContent());
        }
        return id;
    }

    private static String fresh() {
        return "recepcion-" + UUID.randomUUID().toString().substring(0, 8);
    }

    // --- the staff ------------------------------------------------------------------------

    @Test
    void theStaffLogInWithTheTokenTheyAskedForAndTheSessionReadsTheAgenda() throws Exception {
        Browser browser = open("/api/session");
        mockMvc.perform(get("/api/session").with(browser))
                .andExpect(jsonPath("$.user").doesNotExist())
                .andExpect(header().string("Cache-Control", containsString("no-store")));

        String renewed = tokenOf(staffLogIn(browser, "recepcion", "recepcion123")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.username").value("recepcion"))
                .andExpect(jsonPath("$.user.role").value("RECEPTION"))
                .andExpect(jsonPath("$.user.temporaryPassword").value(false)));
        assertThat(renewed).isNotBlank();

        mockMvc.perform(get("/api/session").with(browser))
                .andExpect(jsonPath("$.user.username").value("recepcion"));
        mockMvc.perform(get("/api/agenda").with(browser))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", containsString("no-store")));
    }

    @Test
    void aLoginWithoutTheTokenIsRefusedAndLogsNobodyIn() throws Exception {
        MockHttpSession session = new MockHttpSession();
        mockMvc.perform(post("/api/session").session(session).param("username", "recepcion")
                        .param("password", "recepcion123"))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/agenda").session(session)).andExpect(status().isUnauthorized());
    }

    @Test
    void aWrongPasswordIsA401WithTheErrorBodyAndNoBrowserDialog() throws Exception {
        Browser browser = open("/api/session");
        staffLogIn(browser, "recepcion", "no-es-la-clave")
                .andExpect(status().isUnauthorized())
                .andExpect(header().doesNotExist("WWW-Authenticate"))
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.message").value("Wrong username or password"));
        mockMvc.perform(get("/api/agenda").with(browser)).andExpect(status().isUnauthorized());
    }

    /** A write over the session needs the token. */
    @Test
    void aWriteOverTheSessionNeedsTheToken() throws Exception {
        Browser browser = staff("recepcion", "recepcion123");

        mockMvc.perform(post("/api/appointments").with(browser)
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/appointments").with(browser).header(CSRF, "otro-token")
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isForbidden());
        // With the token, the request reaches the booking, which finds it empty.
        mockMvc.perform(post("/api/appointments").with(browser).header(CSRF, browser.token())
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
        // With neither a session nor a token, it is nobody's.
        mockMvc.perform(post("/api/appointments").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
    }

    /**
     * A screen left open on a session that is gone — the application restarted, or the session ran out — carries a
     * token of that session: it is told the session ended, and goes back to its login, not that it may not.
     */
    @Test
    void aTokenOfASessionThatIsGoneIsASessionEndedNotARefusal() throws Exception {
        Browser browser = staff("recepcion", "recepcion123");
        browser.session().invalidate();

        mockMvc.perform(post("/api/appointments").with(browser).header(CSRF, browser.token())
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("The session was ended"));
        staffLogIn(browser, "recepcion", "recepcion123")
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("The session was ended"));
        // A wrong token of a session that is there is still a refusal.
        Browser other = staff("recepcion", "recepcion123");
        mockMvc.perform(post("/api/appointments").with(other).header(CSRF, browser.token())
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isForbidden());
    }

    /** The session's login is the only way in: Basic authentication is nobody's, staff or patient (D-115). */
    @Test
    void basicAuthenticationIsNoWayIn() throws Exception {
        Patient patient = accounts.withAccount();
        mockMvc.perform(get("/api/patients").with(httpBasic("recepcion", "recepcion123")))
                .andExpect(status().isUnauthorized())
                .andExpect(header().doesNotExist("WWW-Authenticate"));
        mockMvc.perform(get("/api/portal/appointments").with(httpBasic("DNI " + patient.dni(), PASSWORD)))
                .andExpect(status().isUnauthorized());
    }

    /** Credentials of their own do not spare a browser's write its token, as Basic authentication once did. */
    @Test
    void aWriteWithItsOwnCredentialsStillNeedsTheToken() throws Exception {
        Browser browser = staff("recepcion", "recepcion123");

        mockMvc.perform(post("/api/appointments").with(browser).with(httpBasic("recepcion", "recepcion123"))
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void loggingOutNeedsTheTokenAndEndsTheSession() throws Exception {
        Browser browser = staff("recepcion", "recepcion123");
        mockMvc.perform(get("/api/agenda").with(browser)).andExpect(status().isOk());

        mockMvc.perform(post("/api/session/logout").with(browser)).andExpect(status().isForbidden());
        mockMvc.perform(post("/api/session/logout").with(browser).header(CSRF, browser.token()))
                .andExpect(status().isNoContent());

        MockHttpSession after = new MockHttpSession();
        mockMvc.perform(get("/api/agenda").session(after)).andExpect(status().isUnauthorized());
        assertThat(browser.session().isInvalid()).isTrue();
    }

    /** Not when the session runs out: at the very next request (D-68). */
    @Test
    void aDeactivatedUserLosesTheApplicationsSessionAtOnce() throws Exception {
        String username = fresh();
        String id = userId(username, "la-clave-propia", false);
        Browser browser = staff(username, "la-clave-propia");
        mockMvc.perform(get("/api/agenda").with(browser)).andExpect(status().isOk());

        mockMvc.perform(post("/api/users/" + id + "/deactivate").with(staffUser("admin")))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/agenda").with(browser))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("The session was ended"));
    }

    /** The application learns it, to send the user to change it; anything else is refused meanwhile (D-69). */
    @Test
    void aUserWhosePasswordIsTemporaryIsToldSoAndReachesNothingElse() throws Exception {
        String username = fresh();
        userId(username, "la-clave-del-admin", true);
        Browser browser = open("/api/session");

        staffLogIn(browser, username, "la-clave-del-admin")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.temporaryPassword").value(true));
        mockMvc.perform(get("/api/session").with(browser))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.temporaryPassword").value(true));
        mockMvc.perform(get("/api/agenda").with(browser)).andExpect(status().isForbidden());
    }

    /** The screen on the wall is never touched (D-70). */
    @Test
    void theWaitingRoomsSessionDoesNotRunOut() throws Exception {
        Browser browser = staff("sala", "sala123");
        assertThat(browser.session().getMaxInactiveInterval()).isEqualTo(-1);
    }

    // --- the patients -----------------------------------------------------------------------

    @Test
    void aPatientLogsInWithTheirDocumentAndReachesTheirsAndNothingOfTheStaffs() throws Exception {
        Patient patient = accounts.withAccount();
        Browser browser = open("/api/portal/session");
        mockMvc.perform(get("/api/portal/session").with(browser))
                .andExpect(jsonPath("$.loggedIn").value(false))
                .andExpect(jsonPath("$.documentTypes", hasItem("DNI")));

        patientLogIn(browser, patient, PASSWORD)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.loggedIn").value(true))
                .andExpect(jsonPath("$.csrfToken").isNotEmpty());

        mockMvc.perform(get("/api/portal/appointments").with(browser))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", containsString("no-store")));
        mockMvc.perform(get("/api/session").with(browser)).andExpect(jsonPath("$.user").doesNotExist());
        mockMvc.perform(get("/api/agenda").with(browser)).andExpect(status().isUnauthorized());
        // Nor does the staff's session reach the patients' (D-80).
        mockMvc.perform(get("/api/portal/appointments").with(staff("recepcion", "recepcion123")))
                .andExpect(status().isUnauthorized());
    }

    /** Every failure says the same, and counts towards the lock, whatever the way in (D-86). */
    @Test
    void theApplicationsFailedLoginsLockTheAccountAsAnyOther() throws Exception {
        Patient patient = accounts.withAccount();
        Browser browser = open("/api/portal/session");
        for (int i = 0; i < 5; i++) {
            patientLogIn(browser, patient, "no-es-la-clave")
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.message")
                            .value("Wrong document or password, or the account is locked for a while"));
        }
        patientLogIn(browser, patient, PASSWORD)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message")
                        .value("Wrong document or password, or the account is locked for a while"));
        assertThat(jdbc.queryForObject("select locked_until from patient_account where patient_id = ?",
                LocalDateTime.class, UUID.fromString(patient.id()))).isEqualTo(LocalDateTime.of(2026, 3, 10, 0, 15));
    }

    /** Not when the session runs out: at the very next request (D-79). */
    @Test
    void aDeactivatedAccountLosesTheApplicationsSessionAtOnce() throws Exception {
        Patient patient = accounts.withAccount();
        Browser browser = patient(patient);
        mockMvc.perform(get("/api/portal/appointments").with(browser)).andExpect(status().isOk());

        mockMvc.perform(post("/api/patient-accounts/" + accounts.accountOf(patient) + "/deactivate").with(RECEPTION))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/portal/appointments").with(browser))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void aPatientLogsOutWithTheToken() throws Exception {
        Browser browser = patient(accounts.withAccount());

        mockMvc.perform(post("/api/portal/session/logout").with(browser).header(CSRF, browser.token()))
                .andExpect(status().isNoContent());

        assertThat(browser.session().isInvalid()).isTrue();
        mockMvc.perform(get("/api/portal/appointments").session(new MockHttpSession()))
                .andExpect(status().isUnauthorized());
    }
}
