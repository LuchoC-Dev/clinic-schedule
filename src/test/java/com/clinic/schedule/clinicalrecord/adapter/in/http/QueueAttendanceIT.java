package com.clinic.schedule.clinicalrecord.adapter.in.http;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
import com.clinic.schedule.clinicalrecord.PractitionerUserFixture;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * The queue opens the clinical record (D-63): whoever serves the queue reads the record of a
 * patient waiting in it, and only the practitioner who called them writes it — that day, once
 * they were seen as well. "Today" is 10 March 2026.
 */
@SpringBootTest
@AutoConfigureMockMvc
@WithMockUser(roles = "RECEPTION")
class QueueAttendanceIT {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper json;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private UserDetailsService userDetails;

    private UUID patient;
    private String queue;
    private RequestPostProcessor asArias;
    private RequestPostProcessor asBenitez;
    private RequestPostProcessor asStranger;

    @BeforeEach
    void setUp() throws Exception {
        PractitionerFixture practitioners = new PractitionerFixture(mockMvc, json);
        PractitionerUserFixture users = new PractitionerUserFixture(jdbc, userDetails);
        UUID arias = practitioners.workingEveryDay().id();
        UUID benitez = practitioners.workingEveryDay().id();
        asArias = users.userFor(arias);
        asBenitez = users.userFor(benitez);
        asStranger = users.userFor(practitioners.workingEveryDay().id());
        patient = new PatientFixture(mockMvc, json).newPatient();
        queue = json.readTree(mockMvc.perform(post("/api/queues").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"Guardia " + UUID.randomUUID() + "\"}"))
                .andReturn().getResponse().getContentAsString()).get("id").asText();
        for (UUID practitioner : new UUID[] {arias, benitez}) {
            mockMvc.perform(post("/api/queues/" + queue + "/practitioners").contentType(MediaType.APPLICATION_JSON)
                    .content("{\"practitionerId\": \"" + practitioner + "\"}")).andExpect(status().isOk());
        }
        mockMvc.perform(post("/api/queues/" + queue + "/open")).andExpect(status().isOk());
        mockMvc.perform(post("/api/queues/" + queue + "/session/patients").contentType(MediaType.APPLICATION_JSON)
                .content("{\"patientId\": \"" + patient + "\"}")).andExpect(status().isCreated());
    }

    private ResultActions read(RequestPostProcessor as) throws Exception {
        return mockMvc.perform(get("/api/clinical-records/" + patient).with(as));
    }

    private ResultActions consult(RequestPostProcessor as) throws Exception {
        return mockMvc.perform(post("/api/consultations").with(as).contentType(MediaType.APPLICATION_JSON)
                .content("{\"patientId\": \"" + patient + "\", \"notes\": \"Control.\"}"));
    }

    @Test
    void whoServesTheQueueReadsTheRecordOfAWaitingPatientAndWritesNothing() throws Exception {
        read(asArias).andExpect(status().isOk());
        read(asBenitez).andExpect(status().isOk());
        read(asStranger).andExpect(status().isForbidden());
        consult(asArias).andExpect(status().isForbidden());
    }

    @Test
    void onlyThePractitionerWhoCalledThePatientWritesTheRecordThatDay() throws Exception {
        mockMvc.perform(post("/api/my-queue/call-next").with(asArias)).andExpect(status().isOk());

        consult(asArias).andExpect(status().isCreated());
        consult(asBenitez).andExpect(status().isForbidden());
        // Called by Arias, they wait no more: Benítez, who could have called them, reads it no longer.
        read(asBenitez).andExpect(status().isForbidden());

        mockMvc.perform(post("/api/my-queue/finish").with(asArias)).andExpect(status().isOk());
        consult(asArias).andExpect(status().isCreated());
    }

    @Test
    void whoDidNotComeInIsNobodysToWriteButWaitsToBeRead() throws Exception {
        mockMvc.perform(post("/api/my-queue/call-next").with(asArias));
        mockMvc.perform(post("/api/my-queue/did-not-come-in").with(asArias)).andExpect(status().isOk());

        consult(asArias).andExpect(status().isForbidden());
        read(asBenitez).andExpect(status().isOk());
    }
}
