package com.clinic.schedule.patient.presentation.http;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * What is known of a patient besides who they are, over the real stack (D-73): given on registering,
 * changed apart from the identity, every change in its own history, and read by reception alone.
 * "Now" is 10 March 2026 at midnight, and the user is reception's "recepcion".
 */
@SpringBootTest
@AutoConfigureMockMvc
@WithMockUser(username = "recepcion", roles = "RECEPTION")
class PatientDetailsApiIT {

    private static final String FULL = """
            {"birthDate": "1981-03-12",
             "phone": {"number": "(011) 4555-6666", "owner": {"name": "Ana Perez", "relation": "MOTHER"}},
             "email": {"address": "Luis@Mail.com"}}
            """;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper json;

    private String registered() throws Exception {
        return json.readTree(mockMvc.perform(post("/api/patients")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fullName": "Luis Perez"}
                                """))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString()).get("id").asText();
    }

    private ResultActions change(String id, String body) throws Exception {
        return mockMvc.perform(put("/api/patients/" + id + "/details")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    private ResultActions history(String id) throws Exception {
        return mockMvc.perform(get("/api/patients/" + id + "/details-changes"));
    }

    @Test
    void aPatientIsRegisteredWithTheirDetailsEachPhoneAndEmailWithItsOwner() throws Exception {
        mockMvc.perform(post("/api/patients")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fullName": "Luis Perez", "birthDate": "2015-06-01",
                                 "phone": {"number": "11 4555 6666", "owner": {"name": "Ana Perez", "relation": "MOTHER"}},
                                 "email": {"address": "luis@mail.com"}}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.birthDate").value("2015-06-01"))
                .andExpect(jsonPath("$.phone.number").value("1145556666"))
                .andExpect(jsonPath("$.phone.owner.name").value("Ana Perez"))
                .andExpect(jsonPath("$.phone.owner.relation").value("MOTHER"))
                .andExpect(jsonPath("$.email.address").value("luis@mail.com"))
                .andExpect(jsonPath("$.email.owner").isEmpty());
    }

    @Test
    void theDetailsChangeApartFromTheIdentityAndEachChangeIsInTheirHistory() throws Exception {
        String id = registered();

        change(id, FULL)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fullName").value("Luis Perez"))
                .andExpect(jsonPath("$.phone.number").value("01145556666"))
                .andExpect(jsonPath("$.email.address").value("luis@mail.com"));

        history(id)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(3)))
                .andExpect(jsonPath("$[*].changedBy").value(everyItem(is("recepcion"))))
                .andExpect(jsonPath("$[*].changedAt").value(everyItem(is("2026-03-10T00:00:00"))))
                .andExpect(jsonPath("$[?(@.field == 'BIRTH_DATE')].after.value").value("1981-03-12"))
                .andExpect(jsonPath("$[?(@.field == 'PHONE')].after.owner.relation").value("MOTHER"))
                .andExpect(jsonPath("$[?(@.field == 'EMAIL')].before").value(contains(nullValue())));
        mockMvc.perform(get("/api/patients/" + id + "/identity-changes")).andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void savingTheSameDetailsAddsNothingAndLeavingOneOutClearsIt() throws Exception {
        String id = registered();
        change(id, FULL).andExpect(status().isOk());
        change(id, FULL).andExpect(status().isOk());
        history(id).andExpect(jsonPath("$", hasSize(3)));

        change(id, """
                {"birthDate": "1981-03-12", "email": {"address": "luis@mail.com"}}
                """)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.phone").isEmpty());
        history(id)
                .andExpect(jsonPath("$", hasSize(4)))
                .andExpect(jsonPath("$[?(@.field == 'PHONE')]", hasSize(2)))
                .andExpect(jsonPath("$[?(@.before.value == '01145556666')].after").value(contains(nullValue())));
    }

    @Test
    void wrongDetailsAnswer400AndChangeNothing() throws Exception {
        String id = registered();

        change(id, """
                {"phone": {"number": "4555-666"}}
                """).andExpect(status().isBadRequest()).andExpect(jsonPath("$.status").value(400));
        change(id, """
                {"email": {"address": "luis@mail"}}
                """).andExpect(status().isBadRequest());
        change(id, """
                {"birthDate": "2026-03-11"}
                """).andExpect(status().isBadRequest());
        change(id, """
                {"phone": {"number": "", "owner": {"name": "Ana Perez", "relation": "MOTHER"}}}
                """).andExpect(status().isBadRequest());
        change(id, """
                {"phone": {"number": "1145556666", "owner": {"name": "Ana Perez"}}}
                """).andExpect(status().isBadRequest());
        change(id, """
                {"phone": {"number": "1145556666", "owner": {"name": "Ana Perez", "relation": "AUNT"}}}
                """).andExpect(status().isBadRequest());

        history(id).andExpect(jsonPath("$", hasSize(0)));
        mockMvc.perform(get("/api/patients/" + id)).andExpect(jsonPath("$.phone").isEmpty());
    }

    @Test
    void registeringWithWrongDetailsRegistersNobody() throws Exception {
        String name = "Nadie " + UUID.randomUUID().toString().substring(0, 8);

        mockMvc.perform(post("/api/patients")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fullName": "%s", "birthDate": "2027-01-01"}
                                """.formatted(name)))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/patients").param("query", name)).andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void twoPatientsMayShareAnEmail() throws Exception {
        String brother = registered();
        String sister = registered();
        String mothers = """
                {"email": {"address": "ana@mail.com", "owner": {"name": "Ana Perez", "relation": "MOTHER"}}}
                """;

        change(brother, mothers).andExpect(status().isOk());
        change(sister, mothers).andExpect(status().isOk());
    }

    @Test
    void anUnknownPatientAnswers404() throws Exception {
        change(UUID.randomUUID().toString(), FULL).andExpect(status().isNotFound());
        history(UUID.randomUUID().toString()).andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void anAdminReadsNoneOfIt() throws Exception {
        mockMvc.perform(get("/api/patients/" + UUID.randomUUID() + "/details-changes")).andExpect(status().isForbidden());
        change(UUID.randomUUID().toString(), FULL).andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "arias", roles = "PRACTITIONER")
    void aPractitionerReadsNeitherTheContactNorItsHistory() throws Exception {
        mockMvc.perform(get("/api/patients/" + UUID.randomUUID())).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/patients/" + UUID.randomUUID() + "/details-changes")).andExpect(status().isForbidden());
        change(UUID.randomUUID().toString(), FULL).andExpect(status().isForbidden());
    }
}
