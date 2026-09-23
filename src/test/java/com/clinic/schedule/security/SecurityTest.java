package com.clinic.schedule.security;

import static org.hamcrest.Matchers.containsString;
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

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithUserDetails;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Who may do what, with the real users of the development seed (D-34): reception,
 * {@code recepcion}; and two practitioners, {@code arias} and {@code benitez}.
 */
@SpringBootTest
@AutoConfigureMockMvc
class SecurityTest {

    private static final String ARIAS = "11111111-1111-1111-1111-111111111111";
    private static final String BENITEZ = "22222222-2222-2222-2222-222222222222";
    private static final String ARIAS_CONSULTA = "a1111111-0000-0000-0000-000000000001";
    private static final String BENITEZ_CONSULTA = "b2222222-0000-0000-0000-000000000001";
    private static final String ANA = "c3333333-0000-0000-0000-000000000001";
    private static final String JUAN = "c3333333-0000-0000-0000-000000000002";
    /** A Monday no other test books on: Arias works the morning, Benítez the afternoon. */
    private static final String DAY = "2030-01-07";

    @Autowired
    private MockMvc mockMvc;

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
    void aPractitionerAlwaysReadsTheirOwnAgendaWhateverTheyAskFor() throws Exception {
        book(ANA, ARIAS, ARIAS_CONSULTA, "10:00:00");
        book(JUAN, BENITEZ, BENITEZ_CONSULTA, "15:00:00");

        // Reception gets whom it asks for.
        mockMvc.perform(get("/api/appointments").with(httpBasic("recepcion", "recepcion123"))
                        .param("practitionerId", BENITEZ).param("date", DAY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].practitionerId").value(BENITEZ));

        // Arias asks for Benítez's agenda and gets her own.
        mockMvc.perform(get("/api/appointments").with(httpBasic("arias", "arias123"))
                        .param("practitionerId", BENITEZ).param("date", DAY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].practitionerId").value(ARIAS));
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
        mockMvc.perform(get("/agenda").param("practitionerId", BENITEZ).param("date", DAY))
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
        mockMvc.perform(post("/agenda/appointments/00000000-0000-0000-0000-000000000000/cancel").with(csrf())
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
}
