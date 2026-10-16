package com.clinic.schedule.security;

import static com.clinic.schedule.Logins.staffLogIn;
import static com.clinic.schedule.Logins.staffUser;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
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
        staffLogIn(mockMvc, "recepcion", "wrong").andExpect(status().isUnauthorized());
    }

    @Test
    void receptionMayUseTheWholeApi() throws Exception {
        mockMvc.perform(get("/api/patients").with(staffUser("recepcion")))
                .andExpect(status().isOk());
    }

    @Test
    void aPractitionerMayNotReadPatients() throws Exception {
        mockMvc.perform(get("/api/patients").with(staffUser("arias")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403));
    }

    @Test
    void aPractitionerMayNotBook() throws Exception {
        mockMvc.perform(post("/api/appointments").with(staffUser("arias"))
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
        mockMvc.perform(get("/api/appointments").with(staffUser("recepcion"))
                        .param("practitionerId", BENITEZ).param("date", DAY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].practitionerId", not(empty())))
                .andExpect(jsonPath("$[*].practitionerId", everyItem(is(BENITEZ))));

        // Arias gets her own.
        mockMvc.perform(get("/api/appointments").with(staffUser("arias"))
                        .param("practitionerId", ARIAS).param("date", DAY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].practitionerId", not(empty())))
                .andExpect(jsonPath("$[*].practitionerId", everyItem(is(ARIAS))));

        // Asking for Benítez's is refused, not answered with hers (D-39).
        mockMvc.perform(get("/api/appointments").with(staffUser("arias"))
                        .param("practitionerId", BENITEZ).param("date", DAY))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403));
    }

    @Test
    void aPractitionerMayNotRecordTimeOffNorConfigurePractitioners() throws Exception {
        mockMvc.perform(post("/api/time-off").with(staffUser("arias"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"practitionerId": "%s", "date": "%s"}
                                """.formatted(ARIAS, DAY)))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/practitioners").with(staffUser("arias")))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/practitioners/" + ARIAS + "/reasons").with(staffUser("arias"))
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

        staffLogIn(mockMvc, username, "clave123").andExpect(status().isUnauthorized());
        mockMvc.perform(formLogin("/api/session").user(username).password("clave123"))
                .andExpect(status().isUnauthorized());
    }

    private void book(String patient, String practitioner, String reason, String time) throws Exception {
        mockMvc.perform(post("/api/appointments").with(staffUser("recepcion"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"patientId": "%s", "practitionerId": "%s", "reasonId": "%s", "startsAt": "%sT%s"}
                                """.formatted(patient, practitioner, reason, DAY, time)))
                .andExpect(status().isCreated());
    }
}
