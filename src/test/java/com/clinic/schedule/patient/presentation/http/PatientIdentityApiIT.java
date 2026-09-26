package com.clinic.schedule.patient.presentation.http;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Who a patient is, over the real stack (D-60): registered without a name under an alias,
 * identified and verified later, every change in their history. "Now" is 10 March 2026 at
 * midnight, and the user is reception's "recepcion".
 */
@SpringBootTest
@AutoConfigureMockMvc
@WithMockUser(username = "recepcion", roles = "RECEPTION")
class PatientIdentityApiIT {

    private static final AtomicInteger NUMBERS = new AtomicInteger(40_000_000);

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper json;

    private static String uniqueDni() {
        return String.valueOf(NUMBERS.incrementAndGet());
    }

    private JsonNode unidentified() throws Exception {
        return json.readTree(mockMvc.perform(post("/api/patients/unidentified"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString());
    }

    private ResultActions change(String id, String fullName, String dni, boolean seen) throws Exception {
        String document = dni == null ? "" : """
                , "documentType": "DNI", "documentNumber": "%s"
                """.formatted(dni);
        return mockMvc.perform(put("/api/patients/" + id)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"fullName": "%s"%s, "documentSeen": %s}
                        """.formatted(fullName, document, seen)));
    }

    @Test
    void aPatientWhoGivesNoNameIsRegisteredUnderAnAlias() throws Exception {
        mockMvc.perform(post("/api/patients/unidentified"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.alias").value(matchesPattern("NN-\\d{4}")))
                .andExpect(jsonPath("$.identity").value("ALIAS"))
                .andExpect(jsonPath("$.document").isEmpty());
    }

    @Test
    void everyAliasIsAnotherPatients() throws Exception {
        JsonNode first = unidentified();
        JsonNode second = unidentified();

        assertThat(first.get("alias").asText())
                .isNotEqualTo(second.get("alias").asText());
        assertThat(first.get("fullName").asText())
                .isEqualTo(first.get("alias").asText());
    }

    @Test
    void theRealIdentityReplacesTheAliasWhichStillFindsThePatient() throws Exception {
        JsonNode patient = unidentified();
        String id = patient.get("id").asText();
        String alias = patient.get("alias").asText();
        String name = "Ana Pérez " + UUID.randomUUID().toString().substring(0, 8);

        change(id, name, uniqueDni(), true)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fullName").value(name))
                .andExpect(jsonPath("$.alias").value(alias))
                .andExpect(jsonPath("$.identity").value("VERIFIED"));

        mockMvc.perform(get("/api/patients").param("query", alias))
                .andExpect(jsonPath("$[?(@.id == '" + id + "')].fullName").value(name));
    }

    @Test
    void everyChangeIsInTheHistoryWithWhoAndWhen() throws Exception {
        String id = unidentified().get("id").asText();
        String dni = uniqueDni();
        change(id, "Ana Pérez", null, false).andExpect(status().isOk());
        change(id, "Ana Pérez", dni, true).andExpect(status().isOk());

        mockMvc.perform(get("/api/patients/" + id + "/identity-changes"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[*].changedBy").value(everyItem(
                        is("recepcion"))))
                .andExpect(jsonPath("$[*].changedAt").value(everyItem(
                        is("2026-03-10T00:00:00"))))
                .andExpect(jsonPath("$[?(@.after.identity == 'DECLARED')].before.identity").value("ALIAS"))
                .andExpect(jsonPath("$[?(@.after.identity == 'VERIFIED')].after.document.number").value(dni));
    }

    @Test
    void savingTheSameIdentityAddsNothingToTheHistory() throws Exception {
        String id = unidentified().get("id").asText();
        change(id, "Luis Gómez", null, false).andExpect(status().isOk());
        change(id, "Luis Gómez", null, false).andExpect(status().isOk());

        mockMvc.perform(get("/api/patients/" + id + "/identity-changes")).andExpect(jsonPath("$", hasSize(1)));
    }

    @Test
    void aVerifiedIdentityWhoseNameChangesIsDeclaredAgain() throws Exception {
        String id = unidentified().get("id").asText();
        String dni = uniqueDni();
        change(id, "Ana Pérez", dni, true).andExpect(jsonPath("$.identity").value("VERIFIED"));

        change(id, "Ana María Pérez", dni, false).andExpect(jsonPath("$.identity").value("DECLARED"));
    }

    @Test
    void aDocumentSaidToBeSeenThatIsNotThereAnswers400() throws Exception {
        String id = unidentified().get("id").asText();

        change(id, "Ana Pérez", null, true)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
        mockMvc.perform(get("/api/patients/" + id + "/identity-changes")).andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void registeringWithTheDocumentInHandVerifiesAtOnce() throws Exception {
        mockMvc.perform(post("/api/patients")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fullName": "Ana Pérez", "documentType": "DNI", "documentNumber": "%s", "documentSeen": true}
                                """.formatted(uniqueDni())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.identity").value("VERIFIED"))
                .andExpect(jsonPath("$.alias").isEmpty());
        mockMvc.perform(post("/api/patients")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fullName": "Ana Pérez"}
                                """))
                .andExpect(jsonPath("$.identity").value("DECLARED"));
    }

    @Test
    void theHistoryOfAnUnknownPatientAnswers404() throws Exception {
        mockMvc.perform(get("/api/patients/" + UUID.randomUUID() + "/identity-changes"))
                .andExpect(status().isNotFound());
    }
}
