package com.clinic.schedule.arrival.session.http;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

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

import com.clinic.schedule.appointment.PractitionerFixture;
import com.clinic.schedule.clinicalrecord.PractitionerUserFixture;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * The waiting room's screen as the staff's application reads it (D-109): each queue, whom it is seeing and who waits,
 * in order, by their name alone; reached by reception and the waiting room's user, and nobody else.
 */
@SpringBootTest
@AutoConfigureMockMvc
@WithMockUser(username = "recepcion", roles = "RECEPTION")
class WaitingRoomApiIT {

    private static final RequestPostProcessor SALA = httpBasic("sala", "sala123");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper json;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private UserDetailsService userDetails;

    private String queue;
    private String name;
    private RequestPostProcessor asArias;

    @BeforeEach
    void setUp() throws Exception {
        UUID arias = new PractitionerFixture(mockMvc, json).workingEveryDay().id();
        asArias = new PractitionerUserFixture(jdbc, userDetails).userFor(arias);
        name = "Cola " + UUID.randomUUID();
        queue = read(mockMvc.perform(post("/api/queues").contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\": \"" + name + "\", \"room\": \"4\"}"))).get("id").asText();
        mockMvc.perform(post("/api/queues/" + queue + "/practitioners").contentType(MediaType.APPLICATION_JSON)
                .content("{\"practitionerId\": \"" + arias + "\"}")).andExpect(status().isOk());
    }

    private JsonNode read(ResultActions result) throws Exception {
        return json.readTree(result.andReturn().getResponse().getContentAsString());
    }

    private UUID patient(String fullName) throws Exception {
        return UUID.fromString(read(mockMvc.perform(post("/api/patients").contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"fullName": "%s", "documentType": "DNI", "documentNumber": "%d"}
                        """.formatted(fullName, ThreadLocalRandom.current().nextInt(50_000_000, 99_999_999)))))
                .get("id").asText());
    }

    private void arrives(UUID patient) throws Exception {
        mockMvc.perform(post("/api/queues/" + queue + "/session/patients").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"patientId\": \"" + patient + "\", \"amount\": 15000}"))
                .andExpect(status().isCreated());
    }

    private JsonNode room(RequestPostProcessor as) throws Exception {
        for (JsonNode each : read(mockMvc.perform(get("/api/waiting-room").with(as)).andExpect(status().isOk()))) {
            if (each.get("id").asText().equals(queue)) {
                return each;
            }
        }
        throw new AssertionError("The queue is not in the waiting room");
    }

    @Test
    void aClosedQueueShowsNobody() throws Exception {
        JsonNode shown = room(SALA);

        assertThat(shown.get("name").asText()).isEqualTo(name);
        assertThat(shown.get("room").asText()).isEqualTo("4");
        assertThat(shown.get("open").asBoolean()).isFalse();
        assertThat(shown.get("called")).isEmpty();
        assertThat(shown.get("waiting")).isEmpty();
    }

    /** Whom the practitioner called, and who waits, in the order they will be called — their names and nothing more. */
    @Test
    void itShowsWhomThePractitionerCalledAndWhoWaitsInOrderByNameAlone() throws Exception {
        mockMvc.perform(post("/api/queues/" + queue + "/open")).andExpect(status().isOk());
        arrives(patient("Tomás Sala"));
        arrives(patient("Camila Sala"));
        arrives(patient("Pedro Sala"));

        JsonNode before = room(SALA);
        assertThat(before.get("open").asBoolean()).isTrue();
        assertThat(before.get("called")).isEmpty();
        assertThat(before.get("waiting")).extracting(JsonNode::asText).containsExactly("Tomás Sala", "Camila Sala", "Pedro Sala");

        mockMvc.perform(post("/api/my-queue/call-next").with(asArias)).andExpect(status().isOk());

        JsonNode after = room(SALA);
        assertThat(after.get("called")).extracting(JsonNode::asText).containsExactly("Tomás Sala");
        assertThat(after.get("waiting")).extracting(JsonNode::asText).containsExactly("Camila Sala", "Pedro Sala");
        assertThat(after.toString()).doesNotContain("DNI").doesNotContain("patientId");
    }

    @Test
    @WithMockUser(username = "recepcion", roles = "RECEPTION")
    void receptionSeesItToo() throws Exception {
        mockMvc.perform(get("/api/waiting-room")).andExpect(status().isOk());
    }

    @Test
    void nobodyElseReachesIt() throws Exception {
        mockMvc.perform(get("/api/waiting-room").with(asArias)).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/waiting-room").with(httpBasic("admin", "admin123"))).andExpect(status().isForbidden());
    }
}
