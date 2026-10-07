package com.clinic.schedule.arrival.session.http;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
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
import com.clinic.schedule.arrival.session.command.CloseDueSessionsUseCase;
import com.clinic.schedule.clinicalrecord.PractitionerUserFixture;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * A queue at work over the real stack (D-63): reception opens it and brings patients in, the
 * practitioner calls them in the order the rule says, finishes with them or says they did not come
 * in, and the queue closes — by hand or at the end of its day. "Now" is 10 March 2026 at midnight.
 */
@SpringBootTest
@AutoConfigureMockMvc
@WithMockUser(username = "recepcion", roles = "RECEPTION")
class SessionApiIT {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper json;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private UserDetailsService userDetails;

    @Autowired
    private CloseDueSessionsUseCase closeDue;

    private PatientFixture patients;
    private String queue;
    private UUID arias;
    private RequestPostProcessor asArias;

    @BeforeEach
    void setUp() throws Exception {
        patients = new PatientFixture(mockMvc, json);
        arias = new PractitionerFixture(mockMvc, json).workingEveryDay().id();
        asArias = new PractitionerUserFixture(jdbc, userDetails).userFor(arias);
        queue = read(mockMvc.perform(post("/api/queues").contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\": \"Cola " + UUID.randomUUID() + "\"}"))).get("id").asText();
        mockMvc.perform(post("/api/queues/" + queue + "/practitioners").contentType(MediaType.APPLICATION_JSON)
                .content("{\"practitionerId\": \"" + arias + "\"}")).andExpect(status().isOk());
    }

    private JsonNode read(ResultActions result) throws Exception {
        return json.readTree(result.andReturn().getResponse().getContentAsString());
    }

    private ResultActions open() throws Exception {
        return mockMvc.perform(post("/api/queues/" + queue + "/open"));
    }

    private ResultActions arrives(UUID patient, boolean emergency) throws Exception {
        return mockMvc.perform(post("/api/queues/" + queue + "/session/patients").contentType(MediaType.APPLICATION_JSON)
                .content("{\"patientId\": \"" + patient + "\", \"emergency\": " + emergency + ", \"amount\": 15000}"));
    }

    private ResultActions mine(String action) throws Exception {
        return mockMvc.perform(post("/api/my-queue/" + action).with(asArias));
    }

    private List<String> eventTypes() {
        UUID session = jdbc.queryForObject("select id from queue_session where queue_id = ? order by opened_at desc "
                + "fetch first 1 rows only", UUID.class, UUID.fromString(queue));
        return jdbc.queryForList("select event_type from arrival_event where stream_id = ? order by version",
                String.class, session);
    }

    @Test
    void aQueueOpensOnceAndClosesAndANewSessionStartsWhenItReopens() throws Exception {
        mockMvc.perform(get("/api/queues/" + queue + "/session")).andExpect(jsonPath("$.open").value(false));

        open().andExpect(status().isOk()).andExpect(jsonPath("$.open").value(true))
                .andExpect(jsonPath("$.closesByItself").value(true));
        open().andExpect(status().isConflict());
        UUID ana = patients.newPatient();
        arrives(ana, false).andExpect(status().isCreated());

        mockMvc.perform(post("/api/queues/" + queue + "/close")).andExpect(jsonPath("$.open").value(false));
        assertThat(jdbc.queryForObject("select state from session_entry where patient_id = ?", String.class, ana))
                .isEqualTo("NOT_SEEN");

        open().andExpect(jsonPath("$.waiting", empty()));
    }

    @Test
    void emergenciesComeFirstThenEveryoneByArrival() throws Exception {
        open();
        UUID ana = patients.newPatient();
        UUID beto = patients.newPatient();
        UUID carla = patients.newPatient();
        arrives(ana, false);
        arrives(beto, false);
        arrives(carla, true);

        mockMvc.perform(get("/api/queues/" + queue + "/session"))
                .andExpect(jsonPath("$.waiting[*].patientId", contains(carla.toString(), ana.toString(), beto.toString())));
    }

    @Test
    void aPatientComesIntoAnOpenQueueOnceAndMustExist() throws Exception {
        UUID ana = patients.newPatient();
        arrives(ana, false).andExpect(status().isConflict());
        open();

        arrives(ana, false).andExpect(status().isCreated());
        arrives(ana, false).andExpect(status().isConflict());
        arrives(UUID.randomUUID(), false).andExpect(status().isBadRequest());
    }

    @Test
    void theNextPatientIsCalledFinishedAndTheCalledOneIsSaidToHaveComeIn() throws Exception {
        open();
        UUID ana = patients.newPatient();
        arrives(ana, false);

        mine("call-next").andExpect(status().isOk())
                .andExpect(jsonPath("$.called.patientId").value(ana.toString()))
                .andExpect(jsonPath("$.session.waiting", empty()));
        mine("call-next").andExpect(status().isConflict());
        mine("finish").andExpect(status().isOk()).andExpect(jsonPath("$.called").isEmpty())
                .andExpect(jsonPath("$.session.done[0].state").value("SEEN"));
        mine("call-next").andExpect(status().isConflict()).andExpect(jsonPath("$.message").value("Nobody is waiting"));
    }

    @Test
    void whoDidNotComeInIsPassedOverOnceAndOutAfterTheirThirdCall() throws Exception {
        open();
        UUID ana = patients.newPatient();
        UUID beto = patients.newPatient();
        arrives(ana, false);
        arrives(beto, false);

        mine("call-next").andExpect(jsonPath("$.called.patientId").value(ana.toString()));
        mine("did-not-come-in").andExpect(jsonPath("$.session.waiting[0].patientId").value(ana.toString()));
        mine("call-next").andExpect(jsonPath("$.called.patientId").value(beto.toString()));
        mine("finish");
        for (int call = 2; call <= 3; call++) {
            mine("call-next").andExpect(jsonPath("$.called.patientId").value(ana.toString()));
            mine("did-not-come-in");
        }

        mockMvc.perform(get("/api/queues/" + queue + "/session"))
                .andExpect(jsonPath("$.waiting", empty()))
                .andExpect(jsonPath("$.done[?(@.patientId == '" + ana + "')].state").value("DID_NOT_SHOW_UP"));
    }

    @Test
    void receptionMakesAWaitingPatientAnEmergencyOrSaysTheyLeft() throws Exception {
        open();
        UUID ana = patients.newPatient();
        UUID beto = patients.newPatient();
        arrives(ana, false);
        JsonNode session = read(arrives(beto, false));
        String betoEntry = session.get("waiting").get(1).get("id").asText();
        String anaEntry = session.get("waiting").get(0).get("id").asText();

        mockMvc.perform(post("/api/queue-entries/" + betoEntry + "/emergency")).andExpect(status().isOk());
        mockMvc.perform(post("/api/queue-entries/" + anaEntry + "/left")).andExpect(status().isOk());
        mockMvc.perform(post("/api/queue-entries/" + anaEntry + "/left")).andExpect(status().isConflict());

        mockMvc.perform(get("/api/queues/" + queue + "/session"))
                .andExpect(jsonPath("$.waiting[*].patientId", contains(beto.toString())))
                .andExpect(jsonPath("$.waiting[0].emergency").value(true))
                .andExpect(jsonPath("$.done[0].state").value("LEFT"));
        mockMvc.perform(post("/api/queue-entries/" + UUID.randomUUID() + "/left")).andExpect(status().isNotFound());
    }

    @Test
    void aPatientWhoGivesNoNameComesInUnderAnAlias() throws Exception {
        open();

        JsonNode session = read(mockMvc.perform(post("/api/queues/" + queue + "/session/unidentified")
                .contentType(MediaType.APPLICATION_JSON).content("{\"emergency\": true}"))
                .andExpect(status().isCreated()));

        UUID patient = UUID.fromString(session.get("waiting").get(0).get("patientId").asText());
        assertThat(session.get("waiting").get(0).get("emergency").asBoolean()).isTrue();
        assertThat(jdbc.queryForObject("select alias from patient where id = ?", String.class, patient)).startsWith("NN-");
    }

    @Test
    void aClosedQueueLeavesNoNamelessPatientBehind() throws Exception {
        int before = jdbc.queryForObject("select count(*) from patient where alias is not null", Integer.class);

        mockMvc.perform(post("/api/queues/" + queue + "/session/unidentified").contentType(MediaType.APPLICATION_JSON)
                .content("{\"amount\": 15000}")).andExpect(status().isConflict());

        assertThat(jdbc.queryForObject("select count(*) from patient where alias is not null", Integer.class))
                .isEqualTo(before);
    }

    @Test
    void everyStepIsAnEvent() throws Exception {
        open();
        arrives(patients.newPatient(), false);
        mine("call-next");
        mine("finish");
        mockMvc.perform(post("/api/queues/" + queue + "/close"));

        assertThat(eventTypes()).containsExactly("SessionOpened", "PatientArrived", "PatientCalled", "PatientSeen",
                "SessionClosed");
    }

    @Test
    void aQueueClosesByItselfOnceItsDayIsOverUnlessKeptOpen() throws Exception {
        open();
        UUID ana = patients.newPatient();
        arrives(ana, false);
        // Opened the day before, as far as its events say.
        UUID session = jdbc.queryForObject("select session_id from open_queue_session where queue_id = ?", UUID.class,
                UUID.fromString(queue));
        jdbc.update("update arrival_event set payload = replace(payload, '2026-03-10T', '2026-03-09T') "
                + "where stream_id = ? and version = 1", session);

        closeDue.handle();

        mockMvc.perform(get("/api/queues/" + queue + "/session")).andExpect(jsonPath("$.open").value(false));
        assertThat(jdbc.queryForObject("select recorded_by from arrival_event where stream_id = ? order by version desc "
                + "fetch first 1 rows only", String.class, session)).isEqualTo("system");

        open();
        mockMvc.perform(post("/api/queues/" + queue + "/auto-close-off")).andExpect(jsonPath("$.closesByItself").value(false));
        UUID second = jdbc.queryForObject("select session_id from open_queue_session where queue_id = ?", UUID.class,
                UUID.fromString(queue));
        jdbc.update("update arrival_event set payload = replace(payload, '2026-03-10T', '2026-03-09T') "
                + "where stream_id = ? and version = 1", second);
        closeDue.handle();
        mockMvc.perform(get("/api/queues/" + queue + "/session")).andExpect(jsonPath("$.open").value(true));
        mockMvc.perform(post("/api/queues/" + queue + "/auto-close-off")).andExpect(status().isConflict());
    }

    @Test
    void aPractitionerWhoServesNoQueueHasNobodyToCall() throws Exception {
        RequestPostProcessor asStranger = new PractitionerUserFixture(jdbc, userDetails)
                .userFor(new PractitionerFixture(mockMvc, json).workingEveryDay().id());

        mockMvc.perform(get("/api/my-queue").with(asStranger)).andExpect(status().isConflict());
        mockMvc.perform(post("/api/my-queue/call-next").with(asStranger)).andExpect(status().isConflict());
    }

    @Test
    void eachRoleReachesOnlyItsSide() throws Exception {
        mockMvc.perform(post("/api/queues/" + queue + "/open").with(asArias)).andExpect(status().isForbidden());
        mockMvc.perform(post("/api/my-queue/call-next")).andExpect(status().isForbidden());
    }

    @Test
    void thePractitionerSeesTheirQueueInOrder() throws Exception {
        open();
        arrives(patients.newPatient(), false);
        arrives(patients.newPatient(), false);

        mockMvc.perform(get("/api/my-queue").with(asArias))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.queueId").value(queue))
                .andExpect(jsonPath("$.session.waiting", hasSize(2)));
    }
}
