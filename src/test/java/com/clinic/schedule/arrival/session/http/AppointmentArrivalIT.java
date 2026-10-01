package com.clinic.schedule.arrival.session.http;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
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
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import com.clinic.schedule.appointment.PatientFixture;
import com.clinic.schedule.appointment.PractitionerFixture;
import com.clinic.schedule.appointment.PractitionerFixture.CreatedPractitioner;
import com.clinic.schedule.clinicalrecord.PractitionerUserFixture;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Patients with an appointment in the queue, over the real stack (D-64): into their practitioner's
 * queue, kept for them and placed by the time, given to another or kept for nobody — also when the
 * appointment is cancelled. A shared queue served by Arias and Benítez; "today" is 10 March 2026.
 */
@SpringBootTest
@AutoConfigureMockMvc
@WithMockUser(username = "recepcion", roles = "RECEPTION")
class AppointmentArrivalIT {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper json;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private UserDetailsService userDetails;

    private PatientFixture patients;
    private CreatedPractitioner arias;
    private UUID benitez;
    private RequestPostProcessor asArias;
    private RequestPostProcessor asBenitez;
    private String queue;

    @BeforeEach
    void setUp() throws Exception {
        PractitionerFixture practitioners = new PractitionerFixture(mockMvc, json);
        PractitionerUserFixture users = new PractitionerUserFixture(jdbc, userDetails);
        patients = new PatientFixture(mockMvc, json);
        arias = practitioners.workingEveryDay();
        benitez = practitioners.workingEveryDay().id();
        asArias = users.userFor(arias.id());
        asBenitez = users.userFor(benitez);
        queue = read(mockMvc.perform(post("/api/queues").contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\": \"Guardia " + UUID.randomUUID() + "\"}"))).get("id").asText();
        join(arias.id());
        join(benitez);
        mockMvc.perform(post("/api/queues/" + queue + "/open")).andExpect(status().isOk());
    }

    private void join(UUID practitioner) throws Exception {
        mockMvc.perform(post("/api/queues/" + queue + "/practitioners").contentType(MediaType.APPLICATION_JSON)
                .content("{\"practitionerId\": \"" + practitioner + "\"}")).andExpect(status().isOk());
    }

    private JsonNode read(ResultActions result) throws Exception {
        return json.readTree(result.andReturn().getResponse().getContentAsString());
    }

    private String book(UUID patient, String startsAt) throws Exception {
        return read(mockMvc.perform(post("/api/appointments").contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"patientId": "%s", "practitionerId": "%s", "reasonId": "%s", "startsAt": "%s"}
                        """.formatted(patient, arias.id(), arias.consulta(), startsAt)))
                .andExpect(status().isCreated())).get("id").asText();
    }

    private ResultActions arrives(String appointment) throws Exception {
        return mockMvc.perform(post("/api/appointment-arrivals").contentType(MediaType.APPLICATION_JSON)
                .content("{\"appointmentId\": \"" + appointment + "\"}"));
    }

    private JsonNode session() throws Exception {
        return read(mockMvc.perform(get("/api/queues/" + queue + "/session")));
    }

    private String entryOf(UUID patient) throws Exception {
        for (JsonNode entry : session().get("waiting")) {
            if (entry.get("patientId").asText().equals(patient.toString())) {
                return entry.get("id").asText();
            }
        }
        throw new AssertionError("Not waiting: " + patient);
    }

    @Test
    void aPatientWithAnAppointmentComesIntoTheirPractitionersQueueKeptForThem() throws Exception {
        UUID ana = patients.newPatient();
        String appointment = book(ana, "2026-03-10T10:00:00");

        arrives(appointment).andExpect(status().isCreated())
                .andExpect(jsonPath("$.queueId").value(queue))
                .andExpect(jsonPath("$.waiting[0].appointmentId").value(appointment))
                .andExpect(jsonPath("$.waiting[0].appointmentAt").value("2026-03-10T10:00:00"))
                .andExpect(jsonPath("$.waiting[0].reservedForId").value(arias.id().toString()));
        arrives(appointment).andExpect(status().isConflict());
    }

    @Test
    void appointmentsComeBeforeWhoCameWithoutOne() throws Exception {
        UUID walkIn = patients.newPatient();
        mockMvc.perform(post("/api/queues/" + queue + "/session/patients").contentType(MediaType.APPLICATION_JSON)
                .content("{\"patientId\": \"" + walkIn + "\"}"));
        UUID ana = patients.newPatient();
        arrives(book(ana, "2026-03-10T10:00:00"));

        mockMvc.perform(get("/api/queues/" + queue + "/session"))
                .andExpect(jsonPath("$.waiting[*].patientId", contains(ana.toString(), walkIn.toString())));
    }

    @Test
    void onlyTheirPractitionerCallsThemUntilTheyAreGivenToAnother() throws Exception {
        UUID ana = patients.newPatient();
        arrives(book(ana, "2026-03-10T10:00:00"));

        mockMvc.perform(post("/api/my-queue/call-next").with(asBenitez)).andExpect(status().isConflict());
        mockMvc.perform(post("/api/queue-entries/" + entryOf(ana) + "/reassign").contentType(MediaType.APPLICATION_JSON)
                .content("{\"practitionerId\": \"" + benitez + "\"}")).andExpect(status().isOk());

        mockMvc.perform(post("/api/my-queue/call-next").with(asArias)).andExpect(status().isConflict());
        mockMvc.perform(post("/api/my-queue/call-next").with(asBenitez))
                .andExpect(jsonPath("$.called.patientId").value(ana.toString()));
    }

    @Test
    void liftingTheReservationLetsAnyoneCall() throws Exception {
        UUID ana = patients.newPatient();
        arrives(book(ana, "2026-03-10T10:00:00"));

        mockMvc.perform(post("/api/queue-entries/" + entryOf(ana) + "/lift-reservation")).andExpect(status().isOk());

        mockMvc.perform(post("/api/my-queue/call-next").with(asBenitez))
                .andExpect(jsonPath("$.called.patientId").value(ana.toString()));
    }

    @Test
    void whenTheirPractitionerLeavesTheQueueReceptionSeesNobodyCanCallThem() throws Exception {
        UUID ana = patients.newPatient();
        arrives(book(ana, "2026-03-10T10:00:00"));

        mockMvc.perform(delete("/api/queues/" + queue + "/practitioners/" + arias.id())).andExpect(status().isOk());

        mockMvc.perform(get("/api/queues/" + queue + "/session"))
                .andExpect(jsonPath("$.uncallable[*].patientId", contains(ana.toString())));
    }

    @Test
    void aCancelledAppointmentLeavesItsPatientInTheirPlaceForAnyone() throws Exception {
        UUID ana = patients.newPatient();
        String appointment = book(ana, "2026-03-10T10:00:00");
        arrives(appointment);

        mockMvc.perform(post("/api/appointments/" + appointment + "/cancel")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"requestedBy\":\"PATIENT\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/queues/" + queue + "/session"))
                .andExpect(jsonPath("$.waiting[0].patientId").value(ana.toString()))
                .andExpect(jsonPath("$.waiting[0].reservedForId").isEmpty());
        UUID session = UUID.fromString(session().get("sessionId").asText());
        assertThat(jdbc.queryForObject("select event_type from arrival_event where stream_id = ? "
                + "order by version desc fetch first 1 rows only", String.class, session)).isEqualTo("ReservationLifted");
    }

    @Test
    void anAppointmentBringsItsPatientInOnlyOnItsDayIntoAnOpenQueueOfItsPractitioner() throws Exception {
        arrives(book(patients.newPatient(), "2026-03-11T10:00:00")).andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("A patient comes in with an appointment on its own day"));
        arrives(UUID.randomUUID().toString()).andExpect(status().isConflict());

        String today = book(patients.newPatient(), "2026-03-10T11:00:00");
        mockMvc.perform(post("/api/queues/" + queue + "/close"));
        arrives(today).andExpect(status().isConflict()).andExpect(jsonPath("$.message").value("The queue is not open"));

        mockMvc.perform(delete("/api/queues/" + queue + "/practitioners/" + arias.id()));
        arrives(today).andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("The appointment's practitioner serves no queue"));
    }

    @Test
    void aPatientKeptForTheirPractitionerIsReadByThatOneOnly() throws Exception {
        UUID ana = patients.newPatient();
        arrives(book(ana, "2026-03-10T10:00:00"));

        mockMvc.perform(get("/api/clinical-records/" + ana).with(asArias)).andExpect(status().isOk());
        // Benítez serves the queue but may not call Ana; and has no appointment with her.
        mockMvc.perform(get("/api/clinical-records/" + ana).with(asBenitez)).andExpect(status().isForbidden());
        mockMvc.perform(post("/api/queue-entries/" + entryOf(ana) + "/lift-reservation"));
        mockMvc.perform(get("/api/clinical-records/" + ana).with(asBenitez)).andExpect(status().isOk());
    }

    @Test
    void theWaitingListShowsNobodyForAnEmptyQueue() throws Exception {
        mockMvc.perform(get("/api/queues/" + queue + "/session")).andExpect(jsonPath("$.uncallable", empty()));
    }
}
