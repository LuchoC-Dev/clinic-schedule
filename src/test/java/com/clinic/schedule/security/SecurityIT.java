package com.clinic.schedule.security;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.test.context.support.WithUserDetails;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Who may do what, with the real users of the development seed (D-34): reception,
 * {@code recepcion}; and two practitioners, {@code arias} and {@code benitez}.
 */
@SpringBootTest
@AutoConfigureMockMvc
class SecurityIT {

    private static final String ARIAS = "11111111-1111-1111-1111-111111111111";
    private static final String BENITEZ = "22222222-2222-2222-2222-222222222222";
    private static final String ARIAS_CONSULTA = "a1111111-0000-0000-0000-000000000001";
    private static final String BENITEZ_CONSULTA = "b2222222-0000-0000-0000-000000000001";
    private static final String ANA = "c3333333-0000-0000-0000-000000000001";
    private static final String JUAN = "c3333333-0000-0000-0000-000000000002";
    /**
     * A Monday no other test books on, within a year of the tests' clock: Arias works the
     * morning, Benítez the afternoon.
     */
    private static final String DAY = "2026-03-16";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private PasswordEncoder passwords;

    // --- the API ------------------------------------------------------------------------

    @Test
    void theApiAnswers401WithoutCredentials() throws Exception {
        mockMvc.perform(get("/api/patients"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().doesNotExist("WWW-Authenticate"))
                .andExpect(jsonPath("$.status").value(401));
    }

    @Test
    void theApiAnswers401WithAWrongPassword() throws Exception {
        mockMvc.perform(get("/api/patients").with(httpBasic("recepcion", "wrong")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void receptionMayUseTheWholeApi() throws Exception {
        mockMvc.perform(get("/api/patients").with(httpBasic("recepcion", "recepcion123")))
                .andExpect(status().isOk());
    }

    @Test
    void aPractitionerMayNotReadPatients() throws Exception {
        mockMvc.perform(get("/api/patients").with(httpBasic("arias", "arias123")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403));
    }

    @Test
    void aPractitionerMayNotBook() throws Exception {
        mockMvc.perform(post("/api/appointments").with(httpBasic("arias", "arias123"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"patientId": "%s", "practitionerId": "%s", "reasonId": "%s", "startsAt": "%sT09:00:00"}
                                """.formatted(ANA, ARIAS, ARIAS_CONSULTA, DAY)))
                .andExpect(status().isForbidden());
    }

    @Test
    void aPractitionerReadsTheirOwnAgendaAndIsRefusedAnyOtherOne() throws Exception {
        book(ANA, ARIAS, ARIAS_CONSULTA, "10:00:00");
        book(JUAN, BENITEZ, BENITEZ_CONSULTA, "15:00:00");

        // Other tests book on this day too, so what is checked is whose the appointments are.
        // Reception gets whom it asks for.
        mockMvc.perform(get("/api/appointments").with(httpBasic("recepcion", "recepcion123"))
                        .param("practitionerId", BENITEZ).param("date", DAY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].practitionerId", not(empty())))
                .andExpect(jsonPath("$[*].practitionerId", everyItem(is(BENITEZ))));

        // Arias gets her own.
        mockMvc.perform(get("/api/appointments").with(httpBasic("arias", "arias123"))
                        .param("practitionerId", ARIAS).param("date", DAY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].practitionerId", not(empty())))
                .andExpect(jsonPath("$[*].practitionerId", everyItem(is(ARIAS))));

        // Asking for Benítez's is refused, not answered with hers (D-39).
        mockMvc.perform(get("/api/appointments").with(httpBasic("arias", "arias123"))
                        .param("practitionerId", BENITEZ).param("date", DAY))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403));
    }

    @Test
    void aPractitionerMayNotRecordTimeOffNorConfigurePractitioners() throws Exception {
        mockMvc.perform(post("/api/time-off").with(httpBasic("arias", "arias123"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"practitionerId": "%s", "date": "%s"}
                                """.formatted(ARIAS, DAY)))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/practitioners").with(httpBasic("arias", "arias123")))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/practitioners/" + ARIAS + "/reasons").with(httpBasic("arias", "arias123"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "propia", "durationMinutes": 30}
                                """))
                .andExpect(status().isForbidden());
    }

    @Test
    void anInactiveUserCannotLogIn() throws Exception {
        String username = "inactivo" + UUID.randomUUID().toString().substring(0, 6);
        jdbc.update("insert into app_user (id, username, password_hash, role, practitioner_id, active) "
                        + "values (?, ?, ?, 'RECEPTION', null, false)",
                UUID.randomUUID(), username, passwords.encode("clave123"));

        mockMvc.perform(get("/api/patients").with(httpBasic(username, "clave123")))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(formLogin("/login").user(username).password("clave123"))
                .andExpect(redirectedUrl("/login?error"));
    }

    private void book(String patient, String practitioner, String reason, String time) throws Exception {
        mockMvc.perform(post("/api/appointments").with(httpBasic("recepcion", "recepcion123"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"patientId": "%s", "practitionerId": "%s", "reasonId": "%s", "startsAt": "%sT%s"}
                                """.formatted(patient, practitioner, reason, DAY, time)))
                .andExpect(status().isCreated());
    }

    // --- the screens --------------------------------------------------------------------

    @Test
    void theScreensSendAnonymousUsersToTheLoginForm() throws Exception {
        mockMvc.perform(get("/agenda")).andExpect(redirectedUrlPattern("**/login"));
    }

    @Test
    void theLoginFormIsPublic() throws Exception {
        mockMvc.perform(get("/login"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Contraseña")));
    }

    @Test
    void loggingInLeadsToTheAgenda() throws Exception {
        mockMvc.perform(formLogin("/login").user("recepcion").password("recepcion123"))
                .andExpect(redirectedUrl("/agenda"));
    }

    @Test
    void aWrongPasswordReturnsToTheLoginForm() throws Exception {
        mockMvc.perform(formLogin("/login").user("recepcion").password("wrong"))
                .andExpect(redirectedUrl("/login?error"));
    }

    @Test
    @WithUserDetails("arias")
    void aPractitionerSeesOnlyTheirAgendaAndNoActions() throws Exception {
        mockMvc.perform(get("/agenda").param("practitionerId", ARIAS).param("date", DAY))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Dra. Laura Arias")))
                .andExpect(content().string(not(containsString("Dr. Martín Benítez"))))
                .andExpect(content().string(not(containsString("Nuevo turno"))))
                .andExpect(content().string(not(containsString(">Cancelar<"))))
                .andExpect(content().string(not(containsString(">Pacientes<"))));
    }

    @Test
    @WithUserDetails("arias")
    void aPractitionerAskingForEveryonesWeekGetsTheirOwn() throws Exception {
        book(JUAN, BENITEZ, BENITEZ_CONSULTA, "16:00:00");

        mockMvc.perform(get("/agenda").param("date", DAY).param("view", "week"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Dra. Laura Arias")))
                .andExpect(content().string(not(containsString("Dr. Martín Benítez"))))
                .andExpect(content().string(not(containsString(">Todos<"))))
                .andExpect(content().string(not(containsString("Juan Gómez"))));
    }

    /** Only by editing the URL, since the screen offers her no one else; refused all the same (D-39). */
    @Test
    @WithUserDetails("arias")
    void aPractitionerAskingForAnotherAgendaOnTheScreenIsRefused() throws Exception {
        mockMvc.perform(get("/agenda").param("practitionerId", BENITEZ).param("date", DAY))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithUserDetails("arias")
    void aPractitionerMayNotOpenTheBookingNorTheMovingScreen() throws Exception {
        mockMvc.perform(get("/agenda/new").param("practitionerId", ARIAS).param("date", DAY))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/agenda/appointments/00000000-0000-0000-0000-000000000000/reschedule"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithUserDetails("arias")
    void aPractitionerMayNotConfigurePractitionersNorRecordTimeOffOnTheScreens() throws Exception {
        mockMvc.perform(get("/practitioners")).andExpect(status().isForbidden());
        mockMvc.perform(get("/practitioners/" + ARIAS)).andExpect(status().isForbidden());
        mockMvc.perform(post("/time-off").with(csrf()).param("practitionerId", ARIAS).param("date", DAY))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithUserDetails("arias")
    void aPractitionerMayNotReadAPatientsAppointments() throws Exception {
        mockMvc.perform(get("/agenda/patients/" + ANA)).andExpect(status().isForbidden());
    }

    @Test
    @WithUserDetails("arias")
    void aPractitionerMayNotOpenThePatientsScreen() throws Exception {
        mockMvc.perform(get("/patients")).andExpect(status().isForbidden());
    }

    @Test
    @WithUserDetails("arias")
    void aPractitionerMayNotActOnTheAgendaEvenBypassingTheScreen() throws Exception {
        mockMvc.perform(post("/agenda/appointments/00000000-0000-0000-0000-000000000000/cancel")
                        .param("requestedBy", "PATIENT").with(csrf())
                        .param("practitionerId", ARIAS).param("date", DAY))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithUserDetails("recepcion")
    void receptionSeesTheActions() throws Exception {
        mockMvc.perform(get("/agenda").param("practitionerId", ARIAS).param("date", DAY))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Nuevo turno")))
                .andExpect(content().string(containsString(">Pacientes<")));
    }

    @Test
    @WithUserDetails("recepcion")
    void aFormWithoutItsCsrfTokenIsRefused() throws Exception {
        mockMvc.perform(post("/patients").param("fullName", "Sin Token"))
                .andExpect(status().isForbidden());
    }

    @Test
    void loggingOutEndsTheSession() throws Exception {
        MockHttpSession session = (MockHttpSession) mockMvc
                .perform(formLogin("/login").user("recepcion").password("recepcion123"))
                .andExpect(redirectedUrl("/agenda"))
                .andReturn().getRequest().getSession(false);
        mockMvc.perform(get("/agenda").session(session)).andExpect(status().isOk());

        mockMvc.perform(post("/logout").with(csrf()).session(session))
                .andExpect(redirectedUrl("/login?logout"));

        mockMvc.perform(get("/agenda").session(session)).andExpect(redirectedUrlPattern("**/login"));
    }
}
