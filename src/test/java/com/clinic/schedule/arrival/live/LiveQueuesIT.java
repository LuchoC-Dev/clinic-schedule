package com.clinic.schedule.arrival.live;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.support.TransactionTemplate;

import com.clinic.schedule.appointment.PatientFixture;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * The screens hear the queues change as it happens (D-66): a stream of Server-Sent Events that
 * says which queue changed, once the change is committed and never before, and nothing more.
 */
@SpringBootTest
@AutoConfigureMockMvc
@WithMockUser(username = "recepcion", roles = "RECEPTION")
class LiveQueuesIT {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper json;

    @Autowired
    private ApplicationEventPublisher publisher;

    @Autowired
    private TransactionTemplate transaction;

    private MvcResult listening() throws Exception {
        return mockMvc.perform(get("/queue-events")).andExpect(request().asyncStarted()).andReturn();
    }

    private static String heard(MvcResult stream) throws Exception {
        return stream.getResponse().getContentAsString();
    }

    @Test
    void aScreenHearsWhichQueueChangedAsItHappens() throws Exception {
        MvcResult stream = listening();

        String queue = json.readTree(mockMvc.perform(post("/api/queues").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"Cola " + UUID.randomUUID() + "\"}"))
                .andReturn().getResponse().getContentAsString()).get("id").asText();
        assertThat(heard(stream)).contains("event:queue\ndata:" + queue);

        mockMvc.perform(post("/api/queues/" + queue + "/open")).andExpect(status().isOk());
        mockMvc.perform(post("/api/queues/" + queue + "/session/patients").contentType(MediaType.APPLICATION_JSON)
                .content("{\"patientId\": \"" + new PatientFixture(mockMvc, json).newPatient() + "\", \"amount\": 15000}"));

        assertThat(heard(stream).split("data:" + queue, -1)).hasSize(4);
        assertThat(heard(stream)).doesNotContain("patientId");
    }

    @Test
    void nothingIsHeardOfAChangeThatDidNotHappen() throws Exception {
        MvcResult stream = listening();
        UUID kept = UUID.randomUUID();
        UUID undone = UUID.randomUUID();

        assertThatThrownBy(() -> transaction.executeWithoutResult(status -> {
            publisher.publishEvent(new QueueChanged(undone));
            throw new IllegalStateException("the change is refused");
        })).isInstanceOf(IllegalStateException.class);
        transaction.executeWithoutResult(status -> publisher.publishEvent(new QueueChanged(kept)));

        assertThat(heard(stream)).contains("data:" + kept).doesNotContain(undone.toString());
    }

    @Test
    void aRefusedCommandTellsNobody() throws Exception {
        String queue = json.readTree(mockMvc.perform(post("/api/queues").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"Cola " + UUID.randomUUID() + "\"}"))
                .andReturn().getResponse().getContentAsString()).get("id").asText();
        MvcResult stream = listening();

        mockMvc.perform(post("/api/queues/" + queue + "/close")).andExpect(status().isConflict());

        assertThat(heard(stream)).doesNotContain(queue);
    }

    @Test
    @WithMockUser(roles = "PRACTITIONER")
    void aPractitionerListensToo() throws Exception {
        listening();
    }

    @Test
    void theWaitingRoomShowsTheQueuesAndNamesNoPatient() throws Exception {
        String name = "Sala " + UUID.randomUUID().toString().substring(0, 8);
        String queue = json.readTree(mockMvc.perform(post("/api/queues").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"" + name + "\", \"room\": \"7\"}"))
                .andReturn().getResponse().getContentAsString()).get("id").asText();
        mockMvc.perform(post("/api/queues/" + queue + "/open"));
        UUID patient = new PatientFixture(mockMvc, json).newPatient();
        mockMvc.perform(post("/api/queues/" + queue + "/session/patients").contentType(MediaType.APPLICATION_JSON)
                .content("{\"patientId\": \"" + patient + "\", \"amount\": 15000}"));
        String patientName = json.readTree(mockMvc.perform(get("/api/patients/" + patient))
                .andReturn().getResponse().getContentAsString()).get("fullName").asText();

        mockMvc.perform(get("/waiting-room"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(name)))
                .andExpect(content().string(containsString("<td>1</td>")))
                .andExpect(content().string(containsString("id=\"live\"")))
                .andExpect(content().string(not(containsString(patientName))));
    }

    @Test
    @WithMockUser(roles = "PRACTITIONER")
    void theWaitingRoomIsReceptionsForNow() throws Exception {
        mockMvc.perform(get("/waiting-room")).andExpect(status().isForbidden());
    }
}
