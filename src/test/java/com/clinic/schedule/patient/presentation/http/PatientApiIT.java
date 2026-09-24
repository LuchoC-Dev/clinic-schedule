package com.clinic.schedule.patient.presentation.http;

import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import com.clinic.schedule.patient.entity.DocumentType;

/**
 * The patient endpoints over the real stack.
 *
 * <p>Names carry a random suffix and document numbers are drawn from a counter, so the tests
 * share a database without colliding and nothing has to be cleaned up in between. Searching
 * by that suffix is also what makes the search assertions exact.
 */
@SpringBootTest
@AutoConfigureMockMvc
@WithMockUser(roles = "RECEPTION")
class PatientApiIT {

    private static final AtomicInteger NUMBERS = new AtomicInteger(30_000_000);

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper json;

    private String uniqueName(String name) {
        return name + " " + UUID.randomUUID().toString().substring(0, 8);
    }

    private String uniqueDni() {
        return String.valueOf(NUMBERS.incrementAndGet());
    }

    private String body(String fullName, DocumentType type, String number) {
        if (type == null && number == null) {
            return """
                    {"fullName": "%s"}
                    """.formatted(fullName);
        }
        if (type == null) {
            return """
                    {"fullName": "%s", "documentNumber": "%s"}
                    """.formatted(fullName, number);
        }
        if (number == null) {
            return """
                    {"fullName": "%s", "documentType": "%s"}
                    """.formatted(fullName, type);
        }
        return """
                {"fullName": "%s", "documentType": "%s", "documentNumber": "%s"}
                """.formatted(fullName, type, number);
    }

    private JsonNode register(String fullName, DocumentType type, String number) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/patients")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(fullName, type, number)))
                .andExpect(status().isCreated())
                .andReturn();
        return json.readTree(result.getResponse().getContentAsString());
    }

    @Test
    void registeringAnswers201WithTheDocumentAsANestedObject() throws Exception {
        String name = uniqueName("Ana Perez");
        String dni = uniqueDni();

        mockMvc.perform(post("/api/patients")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(name, DocumentType.DNI, dni)))
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.fullName").value(name))
                .andExpect(jsonPath("$.document.type").value("DNI"))
                .andExpect(jsonPath("$.document.number").value(dni));
    }

    @Test
    void aPatientWithoutADocumentIsAccepted() throws Exception {
        JsonNode registered = register(uniqueName("Sin Documento"), null, null);

        assertStoredDocumentIsNull(registered.get("id").asText());
    }

    @Test
    void manyPatientsWithoutADocumentAreAccepted() throws Exception {
        // The unique constraint must not turn "no document" into a single free slot.
        register(uniqueName("Sin Documento"), null, null);
        register(uniqueName("Sin Documento"), null, null);
        JsonNode third = register(uniqueName("Sin Documento"), null, null);

        assertStoredDocumentIsNull(third.get("id").asText());
    }

    @Test
    void theSameNumberUnderADifferentTypeIsAnotherPatient() throws Exception {
        String number = uniqueDni();
        register(uniqueName("Con DNI"), DocumentType.DNI, number);

        mockMvc.perform(post("/api/patients")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(uniqueName("Con Pasaporte"), DocumentType.PASSPORT, number)))
                .andExpect(status().isCreated());
    }

    @Test
    void separatorsDoNotHideADuplicate() throws Exception {
        register(uniqueName("Con Puntos"), DocumentType.DNI, "31222333");

        mockMvc.perform(post("/api/patients")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(uniqueName("Igual Con Puntos"), DocumentType.DNI, "31.222.333")))
                .andExpect(status().isConflict());
    }

    @Test
    void twoPatientsMayShareAName() throws Exception {
        String name = uniqueName("Juan Gomez");

        register(name, DocumentType.DNI, uniqueDni());
        register(name, DocumentType.DNI, uniqueDni());

        mockMvc.perform(get("/api/patients").param("query", name))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    void aRepeatedDocumentAnswers409() throws Exception {
        String dni = uniqueDni();
        register(uniqueName("Primero"), DocumentType.DNI, dni);

        mockMvc.perform(post("/api/patients")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(uniqueName("Segundo"), DocumentType.DNI, dni)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.error").value("Conflict"));
    }

    @Test
    void halfADocumentAnswers400() throws Exception {
        mockMvc.perform(post("/api/patients")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(uniqueName("Solo Numero"), null, "30111222")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));

        mockMvc.perform(post("/api/patients")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(uniqueName("Solo Tipo"), DocumentType.DNI, null)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void aDniWithLettersAnswers400() throws Exception {
        mockMvc.perform(post("/api/patients")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(uniqueName("Dni Raro"), DocumentType.DNI, "3011122A")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("A DNI number has 7 or 8 digits"));
    }

    @Test
    void anUnknownDocumentTypeAnswers400() throws Exception {
        mockMvc.perform(post("/api/patients")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fullName": "Tipo Inventado", "documentType": "CEDULA", "documentNumber": "123"}
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void aMissingNameAnswers400AndNamesTheField() throws Exception {
        mockMvc.perform(post("/api/patients")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"documentType": "DNI", "documentNumber": "30111222"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields.fullName").value("the full name is required"));
    }

    @Test
    void searchingFindsByDocumentNumberToo() throws Exception {
        String dni = uniqueDni();
        register(uniqueName("Buscado"), DocumentType.DNI, dni);

        mockMvc.perform(get("/api/patients").param("query", dni))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].document.number").value(dni));
    }

    @Test
    void searchingIgnoresCase() throws Exception {
        String name = uniqueName("Mariana Lopez");
        register(name, null, null);

        mockMvc.perform(get("/api/patients").param("query", name.toUpperCase()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    void addingTheDocumentLaterIsAllowed() throws Exception {
        JsonNode registered = register(uniqueName("Despues"), null, null);
        String dni = uniqueDni();

        mockMvc.perform(put("/api/patients/" + registered.get("id").asText())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(registered.get("fullName").asText(), DocumentType.DNI, dni)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.document.number").value(dni));
    }

    @Test
    void correctingANameKeepsTheDocument() throws Exception {
        String dni = uniqueDni();
        JsonNode registered = register(uniqueName("Nombre Malescrito"), DocumentType.DNI, dni);
        String corrected = uniqueName("Nombre Bien Escrito");

        mockMvc.perform(put("/api/patients/" + registered.get("id").asText())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(corrected, DocumentType.DNI, dni)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fullName").value(corrected))
                .andExpect(jsonPath("$.document.number").value(dni));
    }

    @Test
    void aWronglyEnteredDocumentCanBeTakenBackOut() throws Exception {
        JsonNode registered = register(uniqueName("Documento Equivocado"), DocumentType.DNI, uniqueDni());

        mockMvc.perform(put("/api/patients/" + registered.get("id").asText())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(registered.get("fullName").asText(), null, null)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.document").value(nullValue()));
    }

    @Test
    void takingAnotherPatientsDocumentAnswers409() throws Exception {
        String taken = uniqueDni();
        register(uniqueName("Dueno"), DocumentType.DNI, taken);
        JsonNode other = register(uniqueName("Otro"), DocumentType.DNI, uniqueDni());

        mockMvc.perform(put("/api/patients/" + other.get("id").asText())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(other.get("fullName").asText(), DocumentType.DNI, taken)))
                .andExpect(status().isConflict());
    }

    @Test
    void anUnknownPatientAnswers404() throws Exception {
        mockMvc.perform(get("/api/patients/" + UUID.randomUUID()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    private void assertStoredDocumentIsNull(String id) throws Exception {
        mockMvc.perform(get("/api/patients/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.document").value(nullValue()));
    }

    // --- protections (D-38) -------------------------------------------------------------

    @Test
    void aNameWithoutLettersAnswers400() throws Exception {
        mockMvc.perform(post("/api/patients")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("!!!???", null, null)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("A full name needs at least one letter"));
    }

    @Test
    void aShortDniAnswers400() throws Exception {
        mockMvc.perform(post("/api/patients")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(uniqueName("Dni Corto"), DocumentType.DNI, "544")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("A DNI number has 7 or 8 digits"));
    }

    @Test
    void anUnknownDocumentTypeNamesTheFieldInTheUsualBody() throws Exception {
        mockMvc.perform(post("/api/patients")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fullName": "Tipo Inventado", "documentType": "CEDULA", "documentNumber": "123"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.fields.documentType").value("has a value of the wrong type"));
    }

    @Test
    void aBodyThatIsNotJsonAnswers400InTheUsualBody() throws Exception {
        mockMvc.perform(post("/api/patients")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fullName\":"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("The request body is not valid JSON"));
    }
}
