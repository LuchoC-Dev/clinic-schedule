package com.clinic.schedule.appointment;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Real patients for the appointment tests, registered through the patient module's public
 * endpoints, as {@link PractitionerFixture} does for practitioners (D-05).
 *
 * <p>Every call registers a new patient without a document, so tests never collide over a
 * document number nor over a patient's own agenda.
 */
public final class PatientFixture {

    private final MockMvc mockMvc;
    private final ObjectMapper json;

    public PatientFixture(MockMvc mockMvc, ObjectMapper json) {
        this.mockMvc = mockMvc;
        this.json = json;
    }

    public UUID newPatient() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/patients")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fullName": "Paciente %s"}
                                """.formatted(UUID.randomUUID().toString().substring(0, 8))))
                .andExpect(status().isCreated())
                .andReturn();
        return UUID.fromString(json.readTree(result.getResponse().getContentAsString()).get("id").asText());
    }
}
