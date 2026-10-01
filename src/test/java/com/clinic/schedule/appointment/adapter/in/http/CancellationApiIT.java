package com.clinic.schedule.appointment.adapter.in.http;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
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
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import com.clinic.schedule.appointment.PatientFixture;
import com.clinic.schedule.appointment.PractitionerFixture;
import com.clinic.schedule.appointment.PractitionerFixture.CreatedPractitioner;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * What is recorded of a cancellation, and who reads it (D-76): reception, whole, and the admins,
 * without the patient and the note. "Today" is 10 March 2026, at midnight, so every cancellation of
 * these tests is made then; each test has a practitioner of its own and searches by them, so the
 * cancellations of other tests do not show.
 */
@SpringBootTest
@AutoConfigureMockMvc
@WithMockUser(username = "recepcion", roles = "RECEPTION")
class CancellationApiIT {

    private static final String TODAY = "2026-03-10";
    private static final RequestPostProcessor ADMIN = user("admin").roles("ADMIN");
    private static final RequestPostProcessor PRACTITIONER = user("arias").roles("PRACTITIONER");

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

    private String book(String startsAt) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/appointments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"patientId": "%s", "practitionerId": "%s", "reasonId": "%s", "startsAt": "%s"}
                                """.formatted(patient, arias.id(), arias.consulta(), startsAt)))
                .andExpect(status().isCreated())
                .andReturn();
        return read(result).get("id").asText();
    }

    private ResultActions cancel(String appointment, String body) throws Exception {
        return mockMvc.perform(post("/api/appointments/" + appointment + "/cancel")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    private JsonNode read(MvcResult result) throws Exception {
        return json.readTree(result.getResponse().getContentAsString());
    }

    private JsonNode list(String path, String from, String to) throws Exception {
        return read(mockMvc.perform(get(path)
                        .param("practitionerId", arias.id().toString())
                        .param("from", from)
                        .param("to", to))
                .andExpect(status().isOk())
                .andReturn());
    }

    @Test
    void aCancellationRecordsWhoWhenHowWhoAskedAndTheNote() throws Exception {
        String appointment = book(TODAY + "T10:00:00");

        JsonNode cancelled = read(cancel(appointment, """
                {"requestedBy": "PATIENT", "note": "  Viaja por trabajo  "}
                """).andExpect(status().isOk()).andReturn());

        JsonNode cancellation = cancelled.get("cancellation");
        assertThat(cancelled.get("status").asText()).isEqualTo("CANCELLED");
        assertThat(cancellation.get("at").asText()).isEqualTo(TODAY + "T00:00:00");
        assertThat(cancellation.get("by").asText()).isEqualTo("recepcion");
        assertThat(cancellation.get("origin").asText()).isEqualTo("BY_HAND");
        assertThat(cancellation.get("requestedBy").asText()).isEqualTo("PATIENT");
        assertThat(cancellation.get("note").asText()).isEqualTo("Viaja por trabajo");
    }

    @Test
    void aBookedAppointmentHasNoCancellation() throws Exception {
        book(TODAY + "T10:00:00");

        JsonNode agenda = read(mockMvc.perform(get("/api/appointments")
                        .param("practitionerId", arias.id().toString())
                        .param("date", TODAY))
                .andExpect(status().isOk())
                .andReturn());

        assertThat(agenda.get(0).get("cancellation").isNull()).isTrue();
    }

    @Test
    void cancellingSaysWhoAskedForIt() throws Exception {
        String appointment = book(TODAY + "T10:00:00");

        cancel(appointment, "{}").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields.requestedBy").value("who asked for the cancellation is required"));
        cancel(appointment, """
                {"requestedBy": "SOMEONE"}
                """).andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/appointments/" + appointment + "/cancel")).andExpect(status().isBadRequest());

        assertThat(jdbc.queryForObject("select status from appointment where id = ?", String.class,
                UUID.fromString(appointment))).isEqualTo("BOOKED");
    }

    @Test
    void aNoteLongerThanItsColumnIsRefused() throws Exception {
        String appointment = book(TODAY + "T10:00:00");

        cancel(appointment, """
                {"requestedBy": "PRACTICE", "note": "%s"}
                """.formatted("x".repeat(201))).andExpect(status().isBadRequest());
        cancel(appointment, """
                {"requestedBy": "PRACTICE", "note": "%s"}
                """.formatted("x".repeat(200))).andExpect(status().isOk());
    }

    @Test
    void aTimeOffCancelsOnThePracticesBehalfAsWhoeverRecordedIt() throws Exception {
        book(TODAY + "T10:00:00");

        JsonNode result = read(mockMvc.perform(post("/api/time-off")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"practitionerId": "%s", "date": "%s"}
                                """.formatted(arias.id(), TODAY)))
                .andExpect(status().isCreated())
                .andReturn());

        JsonNode cancellation = result.get("cancelled").get(0).get("cancellation");
        assertThat(cancellation.get("by").asText()).isEqualTo("recepcion");
        assertThat(cancellation.get("origin").asText()).isEqualTo("TIME_OFF");
        assertThat(cancellation.get("requestedBy").asText()).isEqualTo("PRACTICE");
        assertThat(cancellation.get("at").asText()).isEqualTo(TODAY + "T00:00:00");
    }

    @Test
    void receptionListsTheCancellationsWhole() throws Exception {
        String appointment = book("2026-03-12T10:00:00");
        cancel(appointment, """
                {"requestedBy": "PATIENT", "note": "Se mudó"}
                """).andExpect(status().isOk());

        JsonNode found = list("/api/cancellations", TODAY, TODAY);

        assertThat(found.get("cancellations")).hasSize(1);
        JsonNode entry = found.get("cancellations").get(0);
        assertThat(entry.get("appointmentId").asText()).isEqualTo(appointment);
        assertThat(entry.get("startsAt").asText()).isEqualTo("2026-03-12T10:00:00");
        assertThat(entry.get("practitioner").get("id").asText()).isEqualTo(arias.id().toString());
        assertThat(entry.get("patient").get("id").asText()).isEqualTo(patient.toString());
        assertThat(entry.get("patient").get("fullName").asText()).startsWith("Paciente ");
        assertThat(entry.get("cancelledAt").asText()).isEqualTo(TODAY + "T00:00:00");
        assertThat(entry.get("cancelledBy").asText()).isEqualTo("recepcion");
        assertThat(entry.get("origin").asText()).isEqualTo("BY_HAND");
        assertThat(entry.get("requestedBy").asText()).isEqualTo("PATIENT");
        assertThat(entry.get("note").asText()).isEqualTo("Se mudó");
    }

    @Test
    void theListGoesByTheDayTheCancellationWasMadeAndByPractitioner() throws Exception {
        cancel(book("2026-03-12T10:00:00"), """
                {"requestedBy": "PATIENT"}
                """).andExpect(status().isOk());

        // Made today, for an appointment two days on: found by today, not by the appointment's day.
        assertThat(list("/api/cancellations", TODAY, TODAY).get("cancellations")).hasSize(1);
        assertThat(list("/api/cancellations", "2026-03-12", "2026-03-12").get("cancellations")).isEmpty();
        assertThat(list("/api/cancellations", "2026-03-09", "2026-03-09").get("cancellations")).isEmpty();
        // Another practitioner's are not this one's.
        CreatedPractitioner benitez = new PractitionerFixture(mockMvc, json).workingEveryDay();
        assertThat(read(mockMvc.perform(get("/api/cancellations").param("practitionerId", benitez.id().toString()))
                .andExpect(status().isOk()).andReturn()).get("cancellations")).isEmpty();
        // Without days, the last week.
        JsonNode lastWeek = read(mockMvc.perform(get("/api/cancellations").param("practitionerId", arias.id().toString()))
                .andExpect(status().isOk()).andReturn());
        assertThat(lastWeek.get("from").asText()).isEqualTo("2026-03-04");
        assertThat(lastWeek.get("to").asText()).isEqualTo(TODAY);
        assertThat(lastWeek.get("cancellations")).hasSize(1);
    }

    @Test
    void daysTheWrongWayRoundAreRefused() throws Exception {
        mockMvc.perform(get("/api/cancellations").param("from", "2026-03-10").param("to", "2026-03-09"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void anAppointmentCancelledBeforeAnythingWasRecordedIsNotListed() throws Exception {
        String appointment = book(TODAY + "T10:00:00");
        // As every appointment cancelled before this phase was left: cancelled, and nothing more.
        jdbc.update("update appointment set status = 'CANCELLED' where id = ?", UUID.fromString(appointment));

        assertThat(list("/api/cancellations", "2026-01-01", TODAY).get("cancellations")).isEmpty();
    }

    @Test
    void theAdminsReadTheCancellationsWithoutThePatientNorTheNote() throws Exception {
        cancel(book("2026-03-12T10:00:00"), """
                {"requestedBy": "PRACTICE", "note": "Internada"}
                """).andExpect(status().isOk());

        MvcResult result = mockMvc.perform(get("/api/cancellation-log").with(ADMIN)
                        .param("practitionerId", arias.id().toString())
                        .param("from", TODAY)
                        .param("to", TODAY))
                .andExpect(status().isOk())
                .andReturn();

        String body = result.getResponse().getContentAsString();
        JsonNode entry = read(result).get("cancellations").get(0);
        assertThat(entry.get("cancelledAt").asText()).isEqualTo(TODAY + "T00:00:00");
        assertThat(entry.get("cancelledBy").asText()).isEqualTo("recepcion");
        assertThat(entry.get("origin").asText()).isEqualTo("BY_HAND");
        assertThat(entry.get("requestedBy").asText()).isEqualTo("PRACTICE");
        assertThat(entry.get("practitioner").get("id").asText()).isEqualTo(arias.id().toString());
        assertThat(entry.get("startsAt").asText()).isEqualTo("2026-03-12T10:00:00");
        assertThat(body).doesNotContain("patient", patient.toString(), "Paciente ", "note", "Internada");
    }

    @Test
    void theAdminsDoNotReachReceptionsList() throws Exception {
        mockMvc.perform(get("/api/cancellations").with(ADMIN)).andExpect(status().isForbidden());
    }

    @Test
    void receptionDoesNotReachTheAdminsLog() throws Exception {
        mockMvc.perform(get("/api/cancellation-log")).andExpect(status().isForbidden());
    }

    @Test
    void practitionersReachNeither() throws Exception {
        mockMvc.perform(get("/api/cancellations").with(PRACTITIONER)).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/cancellation-log").with(PRACTITIONER)).andExpect(status().isForbidden());
    }
}
