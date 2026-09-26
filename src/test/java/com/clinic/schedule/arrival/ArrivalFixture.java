package com.clinic.schedule.arrival;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.UUID;

import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * A patient with an appointment today arrives and is called, through the endpoints, as the
 * practice does it since the queues took the arrival over (D-65): the appointment's practitioner
 * gets a queue if they serve none, it is opened if it is closed, the patient comes in with the
 * appointment, and the practitioner calls them. What the check-in of the appointment used to do for
 * the tests of the clinical record — let the practitioner write it — a call does now.
 *
 * <p>Reception's part runs as the test's own user, which is reception in these tests.
 */
public final class ArrivalFixture {

    private final MockMvc mockMvc;
    private final JdbcTemplate jdbc;
    private final ObjectMapper json = new ObjectMapper();

    public ArrivalFixture(MockMvc mockMvc, JdbcTemplate jdbc) {
        this.mockMvc = mockMvc;
        this.jdbc = jdbc;
    }

    /** The appointment's patient came into their practitioner's queue, and waits there. */
    public void arrived(String appointmentId) throws Exception {
        UUID practitioner = jdbc.queryForObject("select practitioner_id from appointment where id = ?", UUID.class,
                UUID.fromString(appointmentId));
        openQueueFor(practitioner);
        mockMvc.perform(post("/api/appointment-arrivals").contentType(MediaType.APPLICATION_JSON)
                .content("{\"appointmentId\": \"" + appointmentId + "\"}")).andExpect(status().isCreated());
    }

    /**
     * The appointment's patient arrived and their practitioner called them: from now on, that day,
     * the practitioner writes their record. Whoever the practitioner had called before is finished
     * with first.
     */
    public void arrivedAndCalled(String appointmentId, RequestPostProcessor asPractitioner) throws Exception {
        arrived(appointmentId);
        mockMvc.perform(post("/api/my-queue/finish").with(asPractitioner));
        mockMvc.perform(post("/api/my-queue/call-next").with(asPractitioner)).andExpect(status().isOk());
    }

    /**
     * Whoever called or saw the patient did it the day before, as far as the queues go: nothing of
     * today's queue keeps the record open any more.
     */
    public void seenTheDayBefore(UUID patientId) {
        jdbc.update("update session_entry set last_called_at = last_called_at - interval '1' day, "
                + "state = 'SEEN' where patient_id = ? and with_practitioner is not null", patientId);
    }

    /** The practitioner's queue, set up if they serve none, and open. */
    public String openQueueFor(UUID practitioner) throws Exception {
        String queue = queueOf(practitioner);
        boolean open = json.readTree(mockMvc.perform(get("/api/queues/" + queue + "/session"))
                .andReturn().getResponse().getContentAsString()).get("open").asBoolean();
        if (!open) {
            mockMvc.perform(post("/api/queues/" + queue + "/open")).andExpect(status().isOk());
        }
        return queue;
    }

    private String queueOf(UUID practitioner) throws Exception {
        List<UUID> served = jdbc.queryForList("select queue_id from queue_membership where practitioner_id = ?",
                UUID.class, practitioner);
        if (!served.isEmpty()) {
            return served.get(0).toString();
        }
        String queue = json.readTree(mockMvc.perform(post("/api/queues").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"Cola " + UUID.randomUUID() + "\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString()).get("id").asText();
        mockMvc.perform(post("/api/queues/" + queue + "/practitioners").contentType(MediaType.APPLICATION_JSON)
                .content("{\"practitionerId\": \"" + practitioner + "\"}")).andExpect(status().isOk());
        return queue;
    }
}
