package com.clinic.schedule.portal.adapter.in.http;

import static com.clinic.schedule.portal.PatientAccountFixture.PASSWORD;
import static com.clinic.schedule.portal.PatientAccountFixture.RECEPTION;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
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
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.context.TestSecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import com.clinic.schedule.appointment.PractitionerFixture;
import com.clinic.schedule.appointment.PractitionerFixture.CreatedPractitioner;
import com.clinic.schedule.portal.KeptAccountMail;
import com.clinic.schedule.portal.PatientAccountFixture;
import com.clinic.schedule.portal.PatientAccountFixture.Patient;
import com.clinic.schedule.portal.api.CancelLinkWriter;
import com.clinic.schedule.portal.domain.BookingConfirmation;
import com.clinic.schedule.reminder.application.usecase.SendRemindersUseCase;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Moving an appointment on the web (D-93): a booked one of the patient's, or of a member they act for, up to a day
 * before it, to a time the agenda offers from tomorrow to 30 days on, with the same practitioner and reason. The same
 * appointment, marked as moved on the web, counted once and not cancelled; an email confirms it with a new link, the
 * old one ends, and the reminder goes out again. "Today" is 10 March 2026, at midnight; the practitioner works every
 * day from 07:00 to 21:00, and none of their reasons is booked on the web.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(KeptAccountMail.class)
class WebMoveIT {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper json;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private CancelLinkWriter links;

    @Autowired
    private SendRemindersUseCase sendReminders;

    private PatientAccountFixture accounts;
    private CreatedPractitioner arias;

    @BeforeEach
    void setUp() throws Exception {
        accounts = new PatientAccountFixture(mockMvc, json);
        arias = practitioner();
    }

    /** The practitioners' fixture sends its requests as whoever the test is: reception, for the while. */
    private CreatedPractitioner practitioner() throws Exception {
        TestSecurityContextHolder.setAuthentication(UsernamePasswordAuthenticationToken.authenticated("recepcion",
                null, List.of(new SimpleGrantedAuthority("ROLE_RECEPTION"))));
        try {
            return new PractitionerFixture(mockMvc, json).workingEveryDay();
        } finally {
            TestSecurityContextHolder.clearContext();
        }
    }

    private static RequestPostProcessor as(Patient patient) {
        return httpBasic("DNI " + patient.dni(), PASSWORD);
    }

    private JsonNode read(ResultActions result) throws Exception {
        return json.readTree(result.andReturn().getResponse().getContentAsString());
    }

    /** Booked at reception, as most are. */
    private String book(Patient patient, CreatedPractitioner practitioner, String startsAt) throws Exception {
        return read(mockMvc.perform(post("/api/appointments").with(RECEPTION).contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"patientId": "%s", "practitionerId": "%s", "reasonId": "%s", "startsAt": "%s"}
                        """.formatted(patient.id(), practitioner.id(), practitioner.consulta(), startsAt)))
                .andExpect(status().isCreated())).get("id").asText();
    }

    private String book(Patient patient, String startsAt) throws Exception {
        return book(patient, arias, startsAt);
    }

    private ResultActions move(Patient who, String appointment, String startsAt) throws Exception {
        return mockMvc.perform(post("/api/portal/appointments/" + appointment + "/move").with(as(who))
                .contentType(MediaType.APPLICATION_JSON).content("{\"startsAt\": \"" + startsAt + "\"}"));
    }

    private List<String> times(Patient who, String appointment, String date) throws Exception {
        return json.convertValue(read(mockMvc.perform(get("/api/portal/appointments/" + appointment + "/move-times")
                        .with(as(who)).param("date", date))
                .andExpect(status().isOk())).get("times"), json.getTypeFactory()
                .constructCollectionType(List.class, String.class));
    }

    private BookingConfirmation lastConfirmationAt(LocalDateTime startsAt) {
        return KeptAccountMail.CONFIRMATIONS.stream()
                .filter(sent -> sent.startsAt().equals(startsAt))
                .reduce((first, last) -> last).orElseThrow();
    }

    private MockHttpSession login(Patient patient) throws Exception {
        return (MockHttpSession) mockMvc.perform(post("/portal/login").with(csrf())
                        .param("documentType", "DNI").param("documentNumber", patient.dni())
                        .param("password", PASSWORD))
                .andReturn().getRequest().getSession(false);
    }

    private static String tokenOf(String link) {
        return link.substring(link.indexOf("token=") + "token=".length());
    }

    @Test
    void aPatientMovesTheirAppointmentAndItIsTheSameOneMarkedAsMovedOnTheWeb() throws Exception {
        Patient patient = accounts.withAccount();
        String appointment = book(patient, "2026-03-12T10:00:00");
        String oldLink = tokenOf(links.linkFor(UUID.fromString(appointment), LocalDateTime.of(2026, 3, 12, 10, 0))
                .orElseThrow());

        assertThat(times(patient, appointment, "2026-03-12")).contains("09:45", "10:15").doesNotContain("10:00");
        assertThat(times(patient, appointment, "2026-03-13")).contains("07:00", "10:00", "20:30");
        move(patient, appointment, "2026-03-13T09:00:00")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(appointment))
                .andExpect(jsonPath("$.patientId").value(patient.id()))
                .andExpect(jsonPath("$.startsAt").value("2026-03-13T09:00:00"))
                .andExpect(jsonPath("$.practitioner").value("Dra. Arias"))
                .andExpect(jsonPath("$.confirmationSent").value(true));

        mockMvc.perform(get("/api/portal/appointments").with(as(patient)))
                .andExpect(jsonPath("$.upcoming.length()").value(1))
                .andExpect(jsonPath("$.upcoming[0].id").value(appointment))
                .andExpect(jsonPath("$.upcoming[0].startsAt").value("2026-03-13T09:00:00"))
                .andExpect(jsonPath("$.upcoming[0].reason").value("consulta"))
                .andExpect(jsonPath("$.upcoming[0].state").value("BOOKED"));
        mockMvc.perform(get("/api/appointments").with(RECEPTION).param("practitionerId", arias.id().toString())
                        .param("date", "2026-03-13"))
                .andExpect(jsonPath("$[0].id").value(appointment))
                .andExpect(jsonPath("$[0].durationMinutes").value(30))
                .andExpect(jsonPath("$[0].bookedVia").value("RECEPTION"))
                .andExpect(jsonPath("$[0].movedOnWeb").value(true));
        mockMvc.perform(get("/agenda").with(RECEPTION).param("practitionerId", arias.id().toString())
                        .param("date", "2026-03-13"))
                .andExpect(content().string(allOf(containsString("· Movido por la web"), not(containsString("· Web<")))));
        mockMvc.perform(get("/agenda/patients/" + patient.id()).with(RECEPTION))
                .andExpect(content().string(containsString("· Movido por la web")));
        assertThat(jdbc.queryForObject("select count(*) from appointment where id = ? and status = 'BOOKED' "
                + "and cancelled_at is null", Integer.class, UUID.fromString(appointment))).isEqualTo(1);
        assertThat(mockMvc.perform(get("/api/cancellations").with(RECEPTION)).andReturn().getResponse()
                .getContentAsString()).doesNotContain(appointment);

        BookingConfirmation confirmation = lastConfirmationAt(LocalDateTime.of(2026, 3, 13, 9, 0));
        assertThat(confirmation.moved()).isTrue();
        assertThat(confirmation.member()).isNull();
        assertThat(confirmation.practitioner()).isEqualTo("Dra. Arias");
        mockMvc.perform(get("/api/portal/cancel-links/" + oldLink)).andExpect(status().isNotFound());
        mockMvc.perform(get("/api/portal/cancel-links/" + confirmation.cancelToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.startsAt").value("2026-03-13T09:00:00"));
    }

    @Test
    void asManyTimesAsWantedWhileADayIsLeftAndOnlyWhereTheWebLets() throws Exception {
        Patient patient = accounts.withAccount();
        String appointment = book(patient, "2026-03-12T10:00:00");
        String today = book(patient, "2026-03-10T20:00:00");
        book(accounts.withAccount(), "2026-03-14T11:00:00");

        move(patient, appointment, "2026-03-14T10:00:00").andExpect(status().isOk());
        move(patient, appointment, "2026-03-14T10:30:00").andExpect(status().isOk());
        move(patient, appointment, "2026-03-14T10:30:00").andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("The appointment is already at that time"));
        assertThat(times(patient, appointment, "2026-03-14")).doesNotContain("10:30", "11:00", "10:45");
        move(patient, appointment, "2026-03-14T11:00:00").andExpect(status().isConflict());
        move(patient, appointment, "2026-03-14T10:40:00").andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("That time is not offered that day"));
        move(patient, appointment, "2026-03-10T15:00:00").andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("The web moves an appointment from tomorrow to 30 days ahead"));
        move(patient, appointment, "2026-04-09T10:00:00").andExpect(status().isOk());
        move(patient, appointment, "2026-04-10T10:00:00").andExpect(status().isConflict());
        mockMvc.perform(get("/api/portal/appointments/" + appointment + "/move-times").with(as(patient))
                        .param("date", "2026-04-10"))
                .andExpect(status().isConflict());

        move(patient, today, "2026-03-12T10:00:00").andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("An appointment is moved on the web up to 24 hours before it"));
        mockMvc.perform(get("/api/portal/appointments/" + today + "/move-times").with(as(patient))
                        .param("date", "2026-03-12"))
                .andExpect(status().isConflict());
        mockMvc.perform(post("/api/portal/appointments/" + appointment + "/cancel").with(as(patient)))
                .andExpect(status().isNoContent());
        move(patient, appointment, "2026-04-08T10:00:00").andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("The appointment is not booked any more"));
    }

    @Test
    void thePatientsOtherAppointmentsStillCountWhoIsBusy() throws Exception {
        Patient patient = accounts.withAccount();
        String appointment = book(patient, "2026-03-12T10:00:00");
        book(patient, practitioner(), "2026-03-13T09:00:00");

        move(patient, appointment, "2026-03-13T09:15:00").andExpect(status().isConflict());
        mockMvc.perform(post("/portal/appointments/" + appointment + "/move").session(login(patient)).with(csrf())
                        .param("startsAt", "2026-03-13T09:15"))
                .andExpect(flash().attribute("error", "Ya hay otro turno del paciente a esa hora: elegí otra."));
        move(patient, appointment, "2026-03-13T09:30:00").andExpect(status().isOk());
    }

    /** The same appointment: it counts once, so a patient at their limit moves it all the same (D-82, D-91). */
    @Test
    void itCountsOnceAgainstTheLimitOfAppointmentsAhead() throws Exception {
        Patient patient = accounts.withAccount();
        String first = book(patient, "2026-03-12T10:00:00");
        book(patient, "2026-03-13T10:00:00");
        book(patient, "2026-03-14T10:00:00");
        move(patient, first, "2026-03-15T10:00:00").andExpect(status().isOk());

        Patient unverified = accounts.registeredOnWeb();
        String only = book(unverified, "2026-03-12T11:00:00");
        move(unverified, only, "2026-03-16T11:00:00").andExpect(status().isOk());
    }

    @Test
    void onlyTheirOwnOrOfAMemberTheyActFor() throws Exception {
        Patient mother = accounts.withAccount("Marta Díaz", "1985-04-02");
        Patient child = accounts.patient("Tomás Díaz", true, "2018-06-01");
        Patient teen = accounts.patient("Lola Díaz", true, "2011-06-01");
        String group = read(mockMvc.perform(post("/api/family-groups").with(RECEPTION)
                .contentType(MediaType.APPLICATION_JSON).content("{\"name\": \"Familia Díaz\"}"))
                .andExpect(status().isCreated())).get("id").asText();
        for (Patient member : List.of(mother, teen)) {
            mockMvc.perform(post("/api/family-groups/" + group + "/members").with(RECEPTION)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"patientId\": \"%s\", \"shares\": false}".formatted(member.id())))
                    .andExpect(status().isOk());
        }
        mockMvc.perform(post("/api/family-groups/" + group + "/children").with(RECEPTION)
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"patientId": "%s", "requestedBy": "%s", "relation": "MOTHER", "proofSeen": true}
                                """.formatted(child.id(), mother.id())))
                .andExpect(status().isOk());
        String childs = book(child, "2026-03-12T10:00:00");
        String teens = book(teen, "2026-03-12T11:00:00");
        String someoneElses = book(accounts.withAccount(), "2026-03-12T12:00:00");

        move(mother, childs, "2026-03-13T10:00:00").andExpect(status().isOk());
        BookingConfirmation confirmation = lastConfirmationAt(LocalDateTime.of(2026, 3, 13, 10, 0));
        assertThat(confirmation.fullName()).isEqualTo("Marta Díaz");
        assertThat(confirmation.member()).isEqualTo("Tomás Díaz");
        assertThat(confirmation.moved()).isTrue();
        mockMvc.perform(get("/api/portal/appointments").with(as(mother)))
                .andExpect(jsonPath("$.family[0].upcoming[0].startsAt").value("2026-03-13T10:00:00"));

        move(mother, teens, "2026-03-13T11:00:00").andExpect(status().isForbidden());
        move(mother, someoneElses, "2026-03-13T12:00:00").andExpect(status().isForbidden());
        move(mother, UUID.randomUUID().toString(), "2026-03-13T12:00:00").andExpect(status().isForbidden());
        mockMvc.perform(get("/api/portal/appointments/" + someoneElses + "/move-times").with(as(mother))
                        .param("date", "2026-03-13"))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/portal/appointments/" + someoneElses + "/move").with(RECEPTION)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"startsAt\": \"2026-03-13T12:00:00\"}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/appointments").with(RECEPTION).param("practitionerId", arias.id().toString())
                        .param("date", "2026-03-12"))
                .andExpect(jsonPath("$[*].id", hasItem(teens)))
                .andExpect(jsonPath("$[*].id", hasItem(someoneElses)));
    }

    /** Its reminder goes out again for the new time, once, at the next run (D-78). */
    @Test
    void theReminderGoesOutAgainForTheNewTime() throws Exception {
        Patient patient = accounts.withAccount();
        mockMvc.perform(put("/api/portal/profile/details").with(as(patient)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\": {\"address\": \"%s\"}}".formatted(PatientAccountFixture.address())))
                .andExpect(status().isOk());
        mockMvc.perform(put("/api/portal/profile/contact-consent").with(as(patient))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"email\": true}"))
                .andExpect(status().isOk());
        String appointment = book(patient, "2026-03-12T10:00:00");

        sendReminders.handle();
        move(patient, appointment, "2026-03-13T09:00:00").andExpect(status().isOk());
        sendReminders.handle();
        sendReminders.handle();

        List<Map<String, Object>> reminded = jdbc.queryForList("select outcome, starts_at from reminder "
                + "where appointment_id = ? order by starts_at", UUID.fromString(appointment));
        assertThat(reminded).hasSize(2);
        assertThat(reminded).allSatisfy(row -> assertThat(row.get("outcome")).isNotEqualTo("NOT_SENT"));
        assertThat(reminded.get(0).get("starts_at").toString()).startsWith("2026-03-12 10:00");
        assertThat(reminded.get(1).get("starts_at").toString()).startsWith("2026-03-13 09:00");
    }

    @Test
    void aPatientMovesOnTheirScreens() throws Exception {
        Patient patient = accounts.withAccount();
        String appointment = book(patient, "2026-03-16T10:00:00");
        String soon = book(patient, "2026-03-10T20:00:00");
        book(accounts.withAccount(), "2026-03-17T11:00:00");
        String someoneElses = book(accounts.withAccount(), "2026-03-17T12:00:00");
        MockHttpSession session = login(patient);

        String page = mockMvc.perform(get("/portal/appointments").session(session))
                .andReturn().getResponse().getContentAsString();
        assertThat(page).contains("/portal/appointments/" + appointment + "/move")
                .doesNotContain("/portal/appointments/" + soon + "/move");
        assertThat(page.split(">Cambiar horario<", -1)).hasSize(2);

        mockMvc.perform(get("/portal/appointments/" + appointment + "/move").session(session))
                .andExpect(status().isOk())
                .andExpect(content().string(allOf(containsString("lunes 16/03/2026 10:00"),
                        containsString("Dra. Arias"), containsString("consulta"), containsString("Nuevo día"),
                        not(containsString("No quedan horarios")))));
        mockMvc.perform(get("/portal/appointments/" + appointment + "/move").session(session)
                        .param("date", "2026-03-17"))
                .andExpect(content().string(allOf(containsString("Dra. Arias, martes 17/03/2026"),
                        containsString("value=\"2026-03-17T10:30\""),
                        not(containsString("value=\"2026-03-17T11:00\"")))));
        mockMvc.perform(get("/portal/appointments/" + soon + "/move").session(session))
                .andExpect(content().string(containsString("Este turno ya no se puede cambiar por la web")));

        mockMvc.perform(post("/portal/appointments/" + appointment + "/move").session(session).with(csrf())
                        .param("startsAt", "2026-03-17T11:00"))
                .andExpect(redirectedUrl("/portal/appointments/" + appointment + "/move?date=2026-03-17"))
                .andExpect(flash().attribute("error", "Ese horario ya no está libre: elegí otro."));
        mockMvc.perform(post("/portal/appointments/" + appointment + "/move").session(session).with(csrf())
                        .param("startsAt", "2026-03-17T10:30"))
                .andExpect(redirectedUrl("/portal/appointments"))
                .andExpect(flash().attribute("message", "Turno cambiado: martes 17/03/2026 10:30 con Dra. Arias. "
                        + "Te mandamos la confirmación por email."));
        mockMvc.perform(post("/portal/appointments/" + soon + "/move").session(session).with(csrf())
                        .param("startsAt", "2026-03-18T10:30"))
                .andExpect(flash().attribute("error", containsString("Falta menos de un día")));
        mockMvc.perform(get("/portal/appointments/" + someoneElses + "/move").session(session))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/portal/appointments/" + someoneElses + "/move").session(session).with(csrf())
                        .param("startsAt", "2026-03-18T10:30"))
                .andExpect(status().isForbidden());
    }
}
