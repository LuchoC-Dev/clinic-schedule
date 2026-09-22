package com.clinic.schedule.patient;

import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * The patient endpoints over the real stack.
 *
 * <p>Names and national IDs are made unique per test with a random suffix, so the tests share
 * a database without colliding and nothing has to be cleaned up in between. Searching by that
 * suffix is also what makes the search assertions exact.
 */
@SpringBootTest
@AutoConfigureMockMvc
class PatientApiTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper json;

    private String unique() {
        return UUID.randomUUID().toString().substring(0, 8);
    }

    private String body(String fullName, String nationalId) {
        if (nationalId == null) {
            return """
                    {"fullName": "%s"}
                    """.formatted(fullName);
        }
        return """
                {"fullName": "%s", "nationalId": "%s"}
                """.formatted(fullName, nationalId);
    }

    private JsonNode register(String fullName, String nationalId) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/patients")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(fullName, nationalId)))
                .andExpect(status().isCreated())
                .andReturn();
        return json.readTree(result.getResponse().getContentAsString());
    }

    @Test
    void registeringAnswers201WithTheStoredPatient() throws Exception {
        String name = "Ana Perez " + unique();
        String nationalId = unique();

        mockMvc.perform(post("/api/patients")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(name, nationalId)))
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.fullName").value(name))
                .andExpect(jsonPath("$.nationalId").value(nationalId));
    }

    @Test
    void aPatientWithoutANationalIdIsAccepted() throws Exception {
        JsonNode registered = register("Sin Documento " + unique(), null);

        assertStoredNationalIdIsNull(registered.get("id").asText());
    }

    @Test
    void manyPatientsWithoutANationalIdAreAccepted() throws Exception {
        // The unique constraint must not turn "no national ID" into a single free slot.
        register("Sin Documento " + unique(), null);
        register("Sin Documento " + unique(), null);
        JsonNode third = register("Sin Documento " + unique(), null);

        assertStoredNationalIdIsNull(third.get("id").asText());
    }

    @Test
    void aBlankNationalIdIsStoredAsAbsent() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/patients")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fullName": "Espacios %s", "nationalId": "   "}
                                """.formatted(unique())))
                .andExpect(status().isCreated())
                .andReturn();

        assertStoredNationalIdIsNull(json.readTree(result.getResponse().getContentAsString())
                .get("id").asText());
    }

    @Test
    void twoPatientsMayShareAName() throws Exception {
        String name = "Juan Gomez " + unique();

        register(name, unique());
        register(name, unique());

        mockMvc.perform(get("/api/patients").param("query", name))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    void aRepeatedNationalIdAnswers409() throws Exception {
        String nationalId = unique();
        register("Primero " + unique(), nationalId);

        mockMvc.perform(post("/api/patients")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("Segundo " + unique(), nationalId)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.error").value("Conflict"));
    }

    @Test
    void aMissingNameAnswers400AndNamesTheField() throws Exception {
        mockMvc.perform(post("/api/patients")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nationalId": "123"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields.fullName").value("the full name is required"));
    }

    @Test
    void searchingFindsByNationalIdToo() throws Exception {
        String nationalId = unique();
        register("Buscado " + unique(), nationalId);

        mockMvc.perform(get("/api/patients").param("query", nationalId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].nationalId").value(nationalId));
    }

    @Test
    void searchingIgnoresCase() throws Exception {
        String suffix = unique();
        register("Mariana Lopez " + suffix, null);

        mockMvc.perform(get("/api/patients").param("query", "MARIANA LOPEZ " + suffix))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    void addingTheNationalIdLaterIsAllowed() throws Exception {
        JsonNode registered = register("Despues " + unique(), null);
        String name = registered.get("fullName").asText();
        String nationalId = unique();

        mockMvc.perform(put("/api/patients/" + registered.get("id").asText())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(name, nationalId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nationalId").value(nationalId));
    }

    @Test
    void correctingANameKeepsTheNationalId() throws Exception {
        String nationalId = unique();
        JsonNode registered = register("Nombre Malescrito " + unique(), nationalId);
        String corrected = "Nombre Bien Escrito " + unique();

        mockMvc.perform(put("/api/patients/" + registered.get("id").asText())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(corrected, nationalId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fullName").value(corrected))
                .andExpect(jsonPath("$.nationalId").value(nationalId));
    }

    @Test
    void takingAnotherPatientsNationalIdAnswers409() throws Exception {
        String taken = unique();
        register("Dueno " + unique(), taken);
        JsonNode other = register("Otro " + unique(), unique());

        mockMvc.perform(put("/api/patients/" + other.get("id").asText())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(other.get("fullName").asText(), taken)))
                .andExpect(status().isConflict());
    }

    @Test
    void anUnknownPatientAnswers404() throws Exception {
        mockMvc.perform(get("/api/patients/" + UUID.randomUUID()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    private void assertStoredNationalIdIsNull(String id) throws Exception {
        mockMvc.perform(get("/api/patients/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nationalId").value(nullValue()));
    }
}
