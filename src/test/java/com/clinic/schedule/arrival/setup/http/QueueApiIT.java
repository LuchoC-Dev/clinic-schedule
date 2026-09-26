package com.clinic.schedule.arrival.setup.http;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Map;
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
import org.springframework.test.web.servlet.ResultActions;

import com.clinic.schedule.appointment.PractitionerFixture;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Setting up queues over the real stack (D-62): a name nobody else has, an optional room, and
 * practitioners who serve one queue at most — every change kept as an event, with who and when.
 * "Now" is 10 March 2026 at midnight, and the user is reception's "recepcion".
 */
@SpringBootTest
@AutoConfigureMockMvc
@WithMockUser(username = "recepcion", roles = "RECEPTION")
class QueueApiIT {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper json;

    @Autowired
    private JdbcTemplate jdbc;

    private PractitionerFixture practitioners;
    private String name;

    @BeforeEach
    void setUp() {
        practitioners = new PractitionerFixture(mockMvc, json);
        name = "Cola " + UUID.randomUUID().toString().substring(0, 8);
    }

    private ResultActions createQueue(String queueName, String room) throws Exception {
        String roomField = room == null ? "" : ", \"room\": \"" + room + "\"";
        return mockMvc.perform(post("/api/queues")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\": \"" + queueName + "\"" + roomField + "}"));
    }

    private String queue(String queueName) throws Exception {
        return json.readTree(createQueue(queueName, null).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString()).get("id").asText();
    }

    private ResultActions add(String queue, UUID practitioner) throws Exception {
        return mockMvc.perform(post("/api/queues/" + queue + "/practitioners")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"practitionerId\": \"" + practitioner + "\"}"));
    }

    @Test
    void aQueueIsSetUpWithItsNameAndRoomAndNobodyServingIt() throws Exception {
        createQueue(name, " 3 ")
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.name").value(name))
                .andExpect(jsonPath("$.room").value("3"))
                .andExpect(jsonPath("$.practitionerIds", empty()));

        mockMvc.perform(get("/api/queues"))
                .andExpect(jsonPath("$[?(@.name == '" + name + "')].room").value("3"));
    }

    @Test
    void aQueueWithoutARoomIsFine() throws Exception {
        createQueue(name, "").andExpect(status().isCreated()).andExpect(jsonPath("$.room").isEmpty());
    }

    @Test
    void twoQueuesDoNotShareANameWhateverTheCase() throws Exception {
        queue(name);

        createQueue(name.toUpperCase(), null)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409));
    }

    @Test
    void aQueueIsRenamedButNotToAnotherQueuesName() throws Exception {
        String id = queue(name);
        String other = "Otra " + name;
        queue(other);

        mockMvc.perform(put("/api/queues/" + id).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"Guardia " + name + "\", \"room\": \"1\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Guardia " + name))
                .andExpect(jsonPath("$.room").value("1"));
        mockMvc.perform(put("/api/queues/" + id).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"" + other + "\"}"))
                .andExpect(status().isConflict());
    }

    @Test
    void aPractitionerServesOneQueueAtMost() throws Exception {
        String first = queue(name);
        String second = queue("Otra " + name);
        UUID arias = practitioners.workingEveryDay().id();

        add(first, arias).andExpect(status().isOk()).andExpect(jsonPath("$.practitionerIds", contains(arias.toString())));
        add(second, arias).andExpect(status().isConflict());
        add(first, arias).andExpect(status().isConflict());

        mockMvc.perform(delete("/api/queues/" + first + "/practitioners/" + arias)).andExpect(status().isOk());
        add(second, arias).andExpect(status().isOk());
    }

    @Test
    void aQueueLeftWithNobodyServingItStays() throws Exception {
        String id = queue(name);
        UUID arias = practitioners.workingEveryDay().id();
        add(id, arias).andExpect(status().isOk());

        mockMvc.perform(delete("/api/queues/" + id + "/practitioners/" + arias))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.practitionerIds", empty()));
        mockMvc.perform(get("/api/queues/" + id)).andExpect(status().isOk()).andExpect(jsonPath("$.name").value(name));
        mockMvc.perform(delete("/api/queues/" + id + "/practitioners/" + arias)).andExpect(status().isConflict());
    }

    @Test
    void onlyAPractitionerWhoExistsJoinsAQueue() throws Exception {
        add(queue(name), UUID.randomUUID()).andExpect(status().isBadRequest());
    }

    @Test
    void anUnknownQueueAnswers404() throws Exception {
        UUID unknown = UUID.randomUUID();
        mockMvc.perform(get("/api/queues/" + unknown)).andExpect(status().isNotFound());
        add(unknown.toString(), practitioners.workingEveryDay().id()).andExpect(status().isNotFound());
    }

    @Test
    void aQueueWithoutANameAnswers400() throws Exception {
        createQueue("", null).andExpect(status().isBadRequest());
    }

    @Test
    void everyChangeIsAnEventWithWhoAndWhen() throws Exception {
        String id = queue(name);
        UUID arias = practitioners.workingEveryDay().id();
        add(id, arias);
        mockMvc.perform(delete("/api/queues/" + id + "/practitioners/" + arias));

        List<Map<String, Object>> events = jdbc.queryForList(
                "select version, event_type, recorded_by, recorded_at from arrival_event where stream_id = ? order by version",
                UUID.fromString(id));
        assertThat(events).extracting(event -> event.get("event_type"))
                .containsExactly("QueueCreated", "PractitionerJoined", "PractitionerLeft");
        assertThat(events).extracting(event -> ((Number) event.get("version")).intValue()).containsExactly(1, 2, 3);
        assertThat(events).extracting(event -> event.get("recorded_by")).containsOnly("recepcion");
        assertThat(events).extracting(event -> event.get("recorded_at").toString()).containsOnly("2026-03-10 00:00:00.0");
    }

    @Test
    @WithMockUser(roles = "PRACTITIONER")
    void aPractitionerDoesNotSetUpQueues() throws Exception {
        createQueue(name, null).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/queues")).andExpect(status().isForbidden());
    }

    @Test
    void theListNamesEveryQueueWithWhoServesIt() throws Exception {
        String id = queue(name);
        UUID arias = practitioners.workingEveryDay().id();
        UUID benitez = practitioners.workingEveryDay().id();
        add(id, arias);
        add(id, benitez);

        mockMvc.perform(get("/api/queues"))
                .andExpect(jsonPath("$[?(@.id == '" + id + "')].practitionerIds[*]", hasSize(2)));
    }
}
