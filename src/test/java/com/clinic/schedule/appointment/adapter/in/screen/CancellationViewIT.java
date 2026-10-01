package com.clinic.schedule.appointment.adapter.in.screen;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import com.clinic.schedule.appointment.PatientFixture;
import com.clinic.schedule.appointment.PractitionerFixture;
import com.clinic.schedule.appointment.PractitionerFixture.CreatedPractitioner;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Cancelling from the agenda says who asked for it, and the screens show what was recorded (D-76):
 * the patient's appointments and reception's list whole, the admins' list without the patient and the
 * note. "Today" is 10 March 2026, at midnight.
 */
@SpringBootTest
@AutoConfigureMockMvc
@WithMockUser(username = "recepcion", roles = "RECEPTION")
class CancellationViewIT {

    private static final String DAY = "2026-03-10";
    private static final RequestPostProcessor ADMIN = user("admin").roles("ADMIN");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private ObjectMapper json;

    private CreatedPractitioner arias;
    private UUID patient;

    @BeforeEach
    void setUp() throws Exception {
        arias = new PractitionerFixture(mockMvc, json).workingEveryDay();
        patient = new PatientFixture(mockMvc, json).newPatient();
    }

    private String book() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/appointments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"patientId": "%s", "practitionerId": "%s", "reasonId": "%s", "startsAt": "%sT10:00:00"}
                                """.formatted(patient, arias.id(), arias.consulta(), DAY)))
                .andExpect(status().isCreated())
                .andReturn();
        return json.readTree(result.getResponse().getContentAsString()).get("id").asText();
    }

    private void cancelFromTheAgenda(String appointment, String requestedBy, String note) throws Exception {
        mockMvc.perform(post("/agenda/appointments/" + appointment + "/cancel").with(csrf())
                        .param("practitionerId", arias.id().toString()).param("date", DAY)
                        .param("requestedBy", requestedBy).param("note", note))
                .andExpect(flash().attribute("message", "Turno cancelado."));
    }

    private String statusOf(String appointment) {
        return jdbc.queryForObject("select status from appointment where id = ?", String.class,
                UUID.fromString(appointment));
    }

    @Test
    void theAgendaOffersToSayWhoAskedForIt() throws Exception {
        book();

        mockMvc.perform(get("/agenda").param("practitionerId", arias.id().toString()).param("date", DAY))
                .andExpect(content().string(containsString("name=\"requestedBy\"")))
                .andExpect(content().string(containsString("el consultorio")))
                .andExpect(content().string(containsString("name=\"note\"")));
    }

    @Test
    void cancellingWithoutSayingWhoAskedCancelsNothing() throws Exception {
        String appointment = book();

        mockMvc.perform(post("/agenda/appointments/" + appointment + "/cancel").with(csrf())
                        .param("practitionerId", arias.id().toString()).param("date", DAY))
                .andExpect(flash().attribute("error", containsString("quién lo pidió")));

        assertThat(statusOf(appointment)).isEqualTo("BOOKED");
    }

    @Test
    void thePatientsAppointmentsSayWhoCancelledWhenWhoAskedAndTheNote() throws Exception {
        String appointment = book();

        cancelFromTheAgenda(appointment, "PATIENT", "Viaja por trabajo");

        assertThat(statusOf(appointment)).isEqualTo("CANCELLED");
        mockMvc.perform(get("/agenda/patients/" + patient))
                .andExpect(content().string(containsString("Por recepcion el mar 10/03/2026 00:00 · lo pidió el paciente")))
                .andExpect(content().string(containsString("Nota: Viaja por trabajo")));
    }

    @Test
    void aCancellationByTimeOffSaysSo() throws Exception {
        book();
        mockMvc.perform(post("/api/time-off")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"practitionerId": "%s", "date": "%s"}
                                """.formatted(arias.id(), DAY)))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/agenda/patients/" + patient))
                .andExpect(content().string(containsString(
                        "Por la ausencia del profesional, cargada por recepcion el mar 10/03/2026 00:00")));
    }

    @Test
    void anAppointmentCancelledBeforeAnythingWasRecordedSaysNothingWasRecorded() throws Exception {
        String appointment = book();
        jdbc.update("update appointment set status = 'CANCELLED' where id = ?", UUID.fromString(appointment));

        mockMvc.perform(get("/agenda/patients/" + patient))
                .andExpect(content().string(containsString("Sin registro de la cancelación")));
    }

    @Test
    void receptionsListShowsThePatientAndTheNote() throws Exception {
        cancelFromTheAgenda(book(), "PRACTICE", "Se reprograma la agenda");

        mockMvc.perform(get("/cancellations").param("practitionerId", arias.id().toString())
                        .param("from", DAY).param("to", DAY))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("/agenda/patients/" + patient)))
                .andExpect(content().string(containsString("Paciente ")))
                .andExpect(content().string(containsString("el consultorio")))
                .andExpect(content().string(containsString("A mano")))
                .andExpect(content().string(containsString("Se reprograma la agenda")));
    }

    @Test
    void theAdminsListLeavesOutThePatientAndTheNote() throws Exception {
        cancelFromTheAgenda(book(), "PATIENT", "Internada");

        mockMvc.perform(get("/cancellation-log").with(ADMIN).param("practitionerId", arias.id().toString())
                        .param("from", DAY).param("to", DAY))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("recepcion")))
                .andExpect(content().string(containsString("el paciente")))
                .andExpect(content().string(containsString("Usuarios")))
                .andExpect(content().string(not(containsString("Internada"))))
                .andExpect(content().string(not(containsString("Paciente "))))
                .andExpect(content().string(not(containsString(patient.toString()))));
    }

    @Test
    void daysTheWrongWayRoundSaySo() throws Exception {
        mockMvc.perform(get("/cancellations").param("from", "2026-03-10").param("to", "2026-03-09"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("El primer día es posterior al último.")));
    }

    @Test
    void eachRolesHeaderLeadsToItsOwnList() throws Exception {
        mockMvc.perform(get("/agenda"))
                .andExpect(content().string(containsString("href=\"/cancellations\"")));
        mockMvc.perform(get("/record-readings").with(ADMIN))
                .andExpect(content().string(containsString("href=\"/cancellation-log\"")))
                .andExpect(content().string(not(containsString("href=\"/cancellations\""))));
    }

    @Test
    void eachRoleReachesOnlyItsOwnList() throws Exception {
        mockMvc.perform(get("/cancellation-log")).andExpect(status().isForbidden());
        mockMvc.perform(get("/cancellations").with(ADMIN)).andExpect(status().isForbidden());
        mockMvc.perform(get("/cancellations").with(user("arias").roles("PRACTITIONER")))
                .andExpect(status().isForbidden());
    }
}
