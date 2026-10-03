package com.clinic.schedule.portal.adapter.in.http;

import static com.clinic.schedule.portal.PatientAccountFixture.PASSWORD;
import static com.clinic.schedule.portal.PatientAccountFixture.RECEPTION;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.context.TestSecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import com.clinic.schedule.appointment.PractitionerFixture;
import com.clinic.schedule.appointment.PractitionerFixture.CreatedPractitioner;
import com.clinic.schedule.portal.KeptAccountMail;
import com.clinic.schedule.portal.PatientAccountFixture;
import com.clinic.schedule.portal.PatientAccountFixture.Patient;
import com.clinic.schedule.portal.api.CancelLinkWriter;
import com.clinic.schedule.portal.domain.SecretToken;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Cancelling on the web and from an email's link (D-83): the patient's own booked appointments, up to a day
 * before, recorded as the patient's asking — by their account, or by the link — and read so by reception and
 * the admins. "Today" is 10 March 2026, at midnight; the practitioner works every day from 07:00 to 21:00.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(KeptAccountMail.class)
class WebCancellationIT {

    private static final RequestPostProcessor ADMIN = user("admin").roles("ADMIN");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper json;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private CancelLinkWriter links;

    private PatientAccountFixture accounts;
    private CreatedPractitioner arias;

    @BeforeEach
    void setUp() throws Exception {
        accounts = new PatientAccountFixture(mockMvc, json);
        // The practitioners' fixture sends its requests as whoever the test is: reception, for the while.
        TestSecurityContextHolder.setAuthentication(UsernamePasswordAuthenticationToken.authenticated("recepcion",
                null, List.of(new SimpleGrantedAuthority("ROLE_RECEPTION"))));
        try {
            arias = new PractitionerFixture(mockMvc, json).workingEveryDay();
        } finally {
            TestSecurityContextHolder.clearContext();
        }
    }

    private static RequestPostProcessor as(Patient patient) {
        return httpBasic("DNI " + patient.dni(), PASSWORD);
    }

    /** Booked at reception, as most are. */
    private String book(Patient patient, String startsAt) throws Exception {
        return json.readTree(mockMvc.perform(post("/api/appointments").with(RECEPTION)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"patientId\": \"%s\", \"practitionerId\": \"%s\", \"reasonId\": \"%s\", "
                                .formatted(patient.id(), arias.id(), arias.consulta())
                                + "\"startsAt\": \"" + startsAt + "\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString()).get("id").asText();
    }

    private String tokenOf(String link) {
        return link.substring(link.indexOf("token=") + "token=".length());
    }

    private JsonNode cancellationOf(String appointment, String route, RequestPostProcessor as) throws Exception {
        JsonNode all = json.readTree(mockMvc.perform(get(route).with(as))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString()).get("cancellations");
        for (JsonNode entry : all) {
            if (entry.get("appointmentId").asText().equals(appointment)) {
                return entry;
            }
        }
        throw new AssertionError("No cancellation of " + appointment);
    }

    @Test
    void aPatientCancelsTheirOwnAppointmentUpToADayBefore() throws Exception {
        Patient patient = accounts.withAccount();
        String appointment = book(patient, "2026-03-12T10:00:00");
        String today = book(patient, "2026-03-10T20:00:00");

        mockMvc.perform(post("/api/portal/appointments/" + today + "/cancel").with(as(patient)))
                .andExpect(status().isConflict());
        mockMvc.perform(post("/api/portal/appointments/" + appointment + "/cancel").with(as(patient)))
                .andExpect(status().isNoContent());
        mockMvc.perform(post("/api/portal/appointments/" + appointment + "/cancel").with(as(patient)))
                .andExpect(status().isConflict());

        mockMvc.perform(get("/api/portal/appointments").with(as(patient)))
                .andExpect(jsonPath("$.upcoming[?(@.id == '%s')].state", appointment).value("CANCELLED"))
                .andExpect(jsonPath("$.upcoming[?(@.id == '%s')].cancellationRequestedBy", appointment)
                        .value("PATIENT"));
        JsonNode atReception = cancellationOf(appointment, "/api/cancellations", RECEPTION);
        assertThat(atReception.get("origin").asText()).isEqualTo("ONLINE");
        assertThat(atReception.get("requestedBy").asText()).isEqualTo("PATIENT");
        assertThat(atReception.get("cancelledBy").asText()).startsWith("portal:");
        assertThat(atReception.get("note").isNull()).isTrue();

        String log = mockMvc.perform(get("/api/cancellation-log").with(ADMIN))
                .andReturn().getResponse().getContentAsString();
        assertThat(log).contains("\"origin\":\"ONLINE\"").doesNotContain(atReception.get("cancelledBy").asText());
        mockMvc.perform(get("/cancellations").with(RECEPTION))
                .andExpect(content().string(allOf(containsString(">el paciente (web)<"),
                        containsString(">Por la web<"))));
        mockMvc.perform(get("/cancellation-log").with(ADMIN))
                .andExpect(content().string(allOf(containsString("paciente (web)"), not(containsString("portal:")))));
        mockMvc.perform(get("/agenda/patients/" + patient.id()).with(RECEPTION))
                .andExpect(content().string(containsString("Por el paciente, en la web, el")));
    }

    @Test
    void onlyTheirOwn() throws Exception {
        Patient patient = accounts.withAccount();
        book(patient, "2026-03-12T10:00:00");
        String someoneElses = book(accounts.withAccount(), "2026-03-12T11:00:00");

        mockMvc.perform(post("/api/portal/appointments/" + someoneElses + "/cancel").with(as(patient)))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/portal/appointments/" + UUID.randomUUID() + "/cancel").with(as(patient)))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/portal/appointments/" + someoneElses + "/cancel").with(RECEPTION))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/appointments").with(RECEPTION).param("practitionerId", arias.id().toString())
                        .param("date", "2026-03-12"))
                .andExpect(jsonPath("$[?(@.id == '%s')].status", someoneElses).value("BOOKED"));
    }

    /** The database keeps a patient's cancellation the patient's asking (D-83). */
    @Test
    void theDatabaseKeepsAWebCancellationThePatients() throws Exception {
        String appointment = book(accounts.withAccount(), "2026-03-18T10:00:00");
        String cancel = "update appointment set status = 'CANCELLED', cancelled_at = cast('2026-03-10 00:00:00' as "
                + "timestamp), cancelled_by = 'email-link', cancellation_origin = ?, cancellation_requested_by = ? "
                + "where id = ?";

        assertThatThrownBy(() -> jdbc.update(cancel, "EMAIL_LINK", "PRACTICE", UUID.fromString(appointment)))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update(cancel, "ONLINE", "PRACTICE", UUID.fromString(appointment)))
                .isInstanceOf(DataIntegrityViolationException.class);
        jdbc.update(cancel, "ONLINE", "PATIENT", UUID.fromString(appointment));
    }

    @Test
    void anEmailsLinkCancelsWithNobodyLoggedInAndSaysNothingOfThePatient() throws Exception {
        Patient patient = accounts.withAccount();
        String appointment = book(patient, "2026-03-13T10:00:00");
        String token = tokenOf(links.linkFor(UUID.fromString(appointment), LocalDateTime.of(2026, 3, 13, 10, 0))
                .orElseThrow());
        assertThat(links.linkFor(UUID.fromString(appointment), LocalDateTime.of(2026, 3, 13, 10, 0)).orElseThrow())
                .startsWith("http://localhost:8080/portal/cancel?token=");

        mockMvc.perform(get("/api/portal/cancel-links/" + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.startsAt").value("2026-03-13T10:00:00"))
                .andExpect(jsonPath("$.practitioner").value("Dra. Arias"))
                .andExpect(content().string(allOf(not(containsString("Ana")), not(containsString("consulta")))));
        mockMvc.perform(post("/api/portal/cancel-links/" + token + "/cancel")).andExpect(status().isNoContent());
        mockMvc.perform(post("/api/portal/cancel-links/" + token + "/cancel")).andExpect(status().isNotFound());
        mockMvc.perform(get("/api/portal/cancel-links/otro")).andExpect(status().isNotFound());

        JsonNode atReception = cancellationOf(appointment, "/api/cancellations", RECEPTION);
        assertThat(atReception.get("origin").asText()).isEqualTo("EMAIL_LINK");
        assertThat(atReception.get("cancelledBy").asText()).isEqualTo("email-link");
        assertThat(atReception.get("requestedBy").asText()).isEqualTo("PATIENT");
        mockMvc.perform(get("/cancellations").with(RECEPTION))
                .andExpect(content().string(allOf(containsString(">el link del email<"),
                        containsString(">Con el link del email<"))));
        mockMvc.perform(get("/agenda/patients/" + patient.id()).with(RECEPTION))
                .andExpect(content().string(containsString("Con el link del email, el")));
    }

    @Test
    void aLinkEndsWhenTheAppointmentMovesOrADayIsLeft() throws Exception {
        Patient patient = accounts.withAccount();
        String moved = book(patient, "2026-03-14T10:00:00");
        String token = tokenOf(links.linkFor(UUID.fromString(moved), LocalDateTime.of(2026, 3, 14, 10, 0))
                .orElseThrow());
        mockMvc.perform(post("/api/appointments/" + moved + "/reschedule").with(RECEPTION)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"startsAt\": \"2026-03-14T11:00:00\"}"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/portal/cancel-links/" + token)).andExpect(status().isNotFound());

        String soon = book(patient, "2026-03-10T20:00:00");
        assertThat(links.linkFor(UUID.fromString(soon), LocalDateTime.of(2026, 3, 10, 20, 0))).isEmpty();
        String late = "a-link-sent-earlier";
        jdbc.update("insert into cancel_link (token_hash, appointment_id, starts_at) values (?, ?, ?)",
                SecretToken.hashOf(late), UUID.fromString(soon), LocalDateTime.of(2026, 3, 10, 20, 0));
        mockMvc.perform(get("/api/portal/cancel-links/" + late)).andExpect(status().isConflict());
        mockMvc.perform(post("/api/portal/cancel-links/" + late + "/cancel")).andExpect(status().isConflict());
    }

    @Test
    void aWebBookingsConfirmationCarriesTheLink() throws Exception {
        mockMvc.perform(put("/api/practitioners/" + arias.id() + "/reasons/" + arias.consulta()).with(RECEPTION)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"consulta\", \"durationMinutes\": 30, \"webBookable\": true}"))
                .andExpect(status().isOk());
        Patient patient = accounts.withAccount();
        String appointment = json.readTree(mockMvc.perform(post("/api/portal/appointments").with(as(patient))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"practitionerId\": \"%s\", \"reasonId\": \"%s\", "
                                .formatted(arias.id(), arias.consulta()) + "\"startsAt\": \"2026-03-15T10:00:00\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString()).get("id").asText();

        String token = KeptAccountMail.CONFIRMATIONS.stream()
                .filter(sent -> sent.startsAt().equals(LocalDateTime.of(2026, 3, 15, 10, 0)))
                .reduce((first, last) -> last).orElseThrow().cancelToken();
        mockMvc.perform(post("/api/portal/cancel-links/" + token + "/cancel")).andExpect(status().isNoContent());
        mockMvc.perform(get("/api/portal/appointments").with(as(patient)))
                .andExpect(jsonPath("$.upcoming[?(@.id == '%s')].state", appointment).value("CANCELLED"));
    }

    @Test
    void aPatientCancelsOnTheirScreensAndFromTheLinksPage() throws Exception {
        Patient patient = accounts.withAccount();
        String appointment = book(patient, "2026-03-16T10:00:00");
        book(patient, "2026-03-10T20:00:00");
        MockHttpSession session = (MockHttpSession) mockMvc.perform(post("/portal/login").with(csrf())
                        .param("documentType", "DNI").param("documentNumber", patient.dni())
                        .param("password", PASSWORD))
                .andReturn().getRequest().getSession(false);

        String page = mockMvc.perform(get("/portal/appointments").session(session))
                .andReturn().getResponse().getContentAsString();
        assertThat(page).contains("/portal/appointments/" + appointment + "/cancel");
        assertThat(page.split("class=\"danger\">Cancelar<", -1)).hasSize(2);
        mockMvc.perform(post("/portal/appointments/" + appointment + "/cancel").session(session).with(csrf()))
                .andExpect(redirectedUrl("/portal/appointments"))
                .andExpect(flash().attribute("message", "Turno cancelado."));
        mockMvc.perform(post("/portal/appointments/" + appointment + "/cancel").session(session).with(csrf()))
                .andExpect(flash().attribute("error", "Ese turno ya no está reservado."));
        String soon = json.readTree(mockMvc.perform(get("/api/portal/appointments").with(as(patient)))
                .andReturn().getResponse().getContentAsString()).get("upcoming").get(0).get("id").asText();
        mockMvc.perform(post("/portal/appointments/" + soon + "/cancel").session(session).with(csrf()))
                .andExpect(flash().attribute("error", containsString("Falta menos de un día")));

        String linked = book(patient, "2026-03-17T10:00:00");
        String token = tokenOf(links.linkFor(UUID.fromString(linked), LocalDateTime.of(2026, 3, 17, 10, 0))
                .orElseThrow());
        mockMvc.perform(get("/portal/cancel").param("token", token))
                .andExpect(status().isOk())
                .andExpect(content().string(allOf(containsString("martes 17/03/2026 10:00"),
                        containsString("Dra. Arias"), not(containsString("Ana Pérez")))));
        mockMvc.perform(post("/portal/cancel").with(csrf()).param("token", token))
                .andExpect(redirectedUrl("/portal/cancel/done"));
        mockMvc.perform(get("/portal/cancel/done")).andExpect(content().string(containsString("Turno cancelado.")));
        mockMvc.perform(get("/portal/cancel").param("token", token))
                .andExpect(content().string(containsString("Este link ya no sirve")));
        mockMvc.perform(post("/portal/cancel").with(csrf()).param("token", token))
                .andExpect(flash().attribute("error", containsString("Este link ya no sirve")));
    }
}
