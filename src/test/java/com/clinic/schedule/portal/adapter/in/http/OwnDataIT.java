package com.clinic.schedule.portal.adapter.in.http;

import static com.clinic.schedule.portal.PatientAccountFixture.PASSWORD;
import static com.clinic.schedule.portal.PatientAccountFixture.RECEPTION;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import com.clinic.schedule.portal.KeptAccountMail;
import com.clinic.schedule.portal.PatientAccountFixture;
import com.clinic.schedule.portal.PatientAccountFixture.Patient;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * A patient's own data, read and changed from their web account (D-81), on the API and on their screen, with
 * the patient module's rules and its history, which names the account and never the patient's document.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(KeptAccountMail.class)
class OwnDataIT {

    private static final String DETAILS = """
            {"phone": {"number": "11 4555-6666"},
             "email": {"address": "ana.madre@mail.com", "owner": {"name": "Rosa Pérez", "relation": "MOTHER"}},
             "insurance": {"insurer": "OSDE", "memberNumber": "61234567801", "plan": "310"}}
            """;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper json;

    private PatientAccountFixture accounts;

    @BeforeEach
    void setUp() {
        accounts = new PatientAccountFixture(mockMvc, json);
    }

    private static RequestPostProcessor as(Patient patient) {
        return httpBasic("DNI " + patient.dni(), PASSWORD);
    }

    private void changeDetails(Patient patient, String body) throws Exception {
        mockMvc.perform(put("/api/portal/profile/details").with(as(patient))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk());
    }

    private JsonNode atReception(Patient patient, String path) throws Exception {
        return json.readTree(mockMvc.perform(get("/api/patients/" + patient.id() + path).with(RECEPTION))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
    }

    @Test
    void aPatientReadsTheirDataAndChangesTheirContactAndInsurance() throws Exception {
        Patient patient = accounts.withAccount();
        mockMvc.perform(get("/api/portal/profile").with(as(patient)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fullName").value("Ana Pérez"))
                .andExpect(jsonPath("$.document").value("DNI " + patient.dni()))
                .andExpect(jsonPath("$.birthDate").value("1990-05-14"))
                // Whether Mi familia offers registering a child, as the group's rules count the age (D-112).
                .andExpect(jsonPath("$.ageBand").value("ADULT"))
                .andExpect(jsonPath("$.phone").isEmpty())
                .andExpect(jsonPath("$.insurance").isEmpty());

        mockMvc.perform(put("/api/portal/profile/details").with(as(patient))
                        .contentType(MediaType.APPLICATION_JSON).content(DETAILS))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.phone.value").value("1145556666"))
                .andExpect(jsonPath("$.phone.owner").isEmpty())
                .andExpect(jsonPath("$.email.value").value("ana.madre@mail.com"))
                .andExpect(jsonPath("$.email.owner.name").value("Rosa Pérez"))
                .andExpect(jsonPath("$.email.owner.relation").value("MOTHER"))
                .andExpect(jsonPath("$.insurance.insurer").value("OSDE"))
                .andExpect(jsonPath("$.insurance.memberNumber").value("61234567801"))
                .andExpect(jsonPath("$.insurance.plan").value("310"));

        JsonNode atReception = atReception(patient, "");
        assertThat(atReception.get("birthDate").asText()).isEqualTo("1990-05-14");
        assertThat(atReception.at("/email/address").asText()).isEqualTo("ana.madre@mail.com");
        JsonNode history = atReception(patient, "/details-changes");
        assertThat(history.findValuesAsText("field")).containsExactlyInAnyOrder("PHONE", "EMAIL", "INSURANCE");
        assertThat(history.findValuesAsText("changedBy")).allSatisfy(by -> assertThat(by).startsWith("portal:")
                .doesNotContain(patient.dni()));

        mockMvc.perform(put("/api/portal/profile/details").with(as(patient))
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(jsonPath("$.phone").isEmpty())
                .andExpect(jsonPath("$.email").isEmpty())
                .andExpect(jsonPath("$.insurance").isEmpty())
                .andExpect(jsonPath("$.birthDate").value("1990-05-14"));
    }

    @Test
    void anAdolescentsDataSaysTheyAreOne() throws Exception {
        Patient teen = accounts.withAccount("Lola Díaz", "2012-03-11");
        mockMvc.perform(get("/api/portal/profile").with(as(teen)))
                .andExpect(jsonPath("$.ageBand").value("ADOLESCENT"));
        Patient birthday = accounts.withAccount("Tomás Díaz", "2008-03-10");
        mockMvc.perform(get("/api/portal/profile").with(as(birthday)))
                .andExpect(jsonPath("$.ageBand").value("ADULT"));
    }

    @Test
    void whatCannotBeRightIsRefusedAndNothingChanges() throws Exception {
        Patient patient = accounts.withAccount();
        changeDetails(patient, DETAILS);

        for (String wrong : new String[] {"{\"phone\": {\"number\": \"123\"}}",
                "{\"email\": {\"address\": \"ana\"}}",
                "{\"email\": {\"address\": \"ana@mail.com\", \"owner\": {\"name\": \"Rosa\"}}}",
                "{\"email\": {\"address\": \"ana@mail.com\", \"owner\": {\"name\": \"Rosa\", \"relation\": \"AUNT\"}}}",
                "{\"insurance\": {\"memberNumber\": \"61234567801\"}}"}) {
            mockMvc.perform(put("/api/portal/profile/details").with(as(patient))
                            .contentType(MediaType.APPLICATION_JSON).content(wrong))
                    .andExpect(status().isBadRequest());
        }
        mockMvc.perform(get("/api/portal/profile").with(as(patient)))
                .andExpect(jsonPath("$.phone.value").value("1145556666"))
                .andExpect(jsonPath("$.insurance.insurer").value("OSDE"));
    }

    /** Yes is theirs, no withdraws any, and a channel left out stays as it was (D-81). */
    @Test
    void aPatientAgreesOrNotToBeRemindedOnEachChannel() throws Exception {
        Patient patient = accounts.withAccount();
        changeDetails(patient, DETAILS);
        mockMvc.perform(put("/api/patients/" + patient.id() + "/contact-consent").with(RECEPTION)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"email\": \"OWNER\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(put("/api/portal/profile/contact-consent").with(as(patient))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"phone\": true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.phone.consent").value("PATIENT"))
                .andExpect(jsonPath("$.email.consent").value("OWNER"));
        mockMvc.perform(put("/api/portal/profile/contact-consent").with(as(patient))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"email\": false}"))
                .andExpect(jsonPath("$.phone.consent").value("PATIENT"))
                .andExpect(jsonPath("$.email.consent").isEmpty());

        changeDetails(patient, DETAILS.replace("11 4555-6666", "11 4555-7777"));
        mockMvc.perform(get("/api/portal/profile").with(as(patient)))
                .andExpect(jsonPath("$.phone.consent").isEmpty());

        changeDetails(patient, "{}");
        mockMvc.perform(put("/api/portal/profile/contact-consent").with(as(patient))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"phone\": true}"))
                .andExpect(status().isBadRequest());
        assertThat(atReception(patient, "/details-changes").findValuesAsText("field")).contains("PHONE_CONSENT",
                "EMAIL_CONSENT");
    }

    @Test
    void onlyThePatientReachesTheirOwnData() throws Exception {
        Patient patient = accounts.withAccount();

        mockMvc.perform(get("/api/portal/profile").with(RECEPTION)).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/patients/" + patient.id()).with(as(patient))).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/portal/profile")).andExpect(status().isUnauthorized());
    }
}
