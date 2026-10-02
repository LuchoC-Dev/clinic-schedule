package com.clinic.schedule.portal.adapter.in.http;

import static com.clinic.schedule.portal.PatientAccountFixture.PASSWORD;
import static com.clinic.schedule.portal.PatientAccountFixture.RECEPTION;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
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
        mockMvc.perform(get("/patients/" + patient.id()).with(RECEPTION))
                .andExpect(content().string(containsString("el paciente (web)")));

        mockMvc.perform(put("/api/portal/profile/details").with(as(patient))
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(jsonPath("$.phone").isEmpty())
                .andExpect(jsonPath("$.email").isEmpty())
                .andExpect(jsonPath("$.insurance").isEmpty())
                .andExpect(jsonPath("$.birthDate").value("1990-05-14"));
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

    @Test
    void aPatientChangesTheirDataOnTheirScreen() throws Exception {
        Patient patient = accounts.withAccount();
        MockHttpSession session = (MockHttpSession) mockMvc.perform(post("/portal/login").with(csrf())
                        .param("documentType", "DNI").param("documentNumber", patient.dni())
                        .param("password", PASSWORD))
                .andReturn().getRequest().getSession(false);

        mockMvc.perform(get("/portal/profile").session(session))
                .andExpect(status().isOk())
                .andExpect(content().string(allOf(containsString("Ana Pérez"), containsString("DNI " + patient.dni()),
                        containsString("nacimiento 14/05/1990"), containsString("Cargá un teléfono o un email"),
                        containsString(">Mis datos<"))));

        mockMvc.perform(post("/portal/profile/details").session(session).with(csrf())
                        .param("phoneNumber", "11 4555-6666").param("phoneOwnerRelation", "")
                        .param("phoneOwnerName", "").param("emailAddress", "ana.madre@mail.com")
                        .param("emailOwnerRelation", "MOTHER").param("emailOwnerName", "Rosa Pérez")
                        .param("insurer", "OSDE").param("memberNumber", "61234567801").param("plan", "310"))
                .andExpect(redirectedUrl("/portal/profile"))
                .andExpect(flash().attribute("message", "Tus datos quedaron guardados."));
        mockMvc.perform(post("/portal/profile/details").session(session).with(csrf()).param("phoneNumber", "123"))
                .andExpect(flash().attribute("error", containsString("de 8 a 15 dígitos")));
        mockMvc.perform(put("/api/patients/" + patient.id() + "/contact-consent").with(RECEPTION)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"email\": \"OWNER\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/portal/profile").session(session))
                .andExpect(content().string(allOf(containsString("1145556666"), containsString("ana.madre@mail.com"),
                        containsString("value=\"MOTHER\" selected=\"selected\""), containsString("OSDE"),
                        containsString("Sí: lo autorizó Rosa Pérez"), containsString("Sí, lo autorizo yo"),
                        not(containsString("Cargá un teléfono o un email")))))
                // Nobody agreed on the phone, so "No" comes first: saving untouched never agrees.
                .andExpect(result -> assertThat(result.getResponse().getContentAsString())
                        .containsPattern("name=\"phoneConsent\">\\s*<option value=\"false\">No</option>"));

        mockMvc.perform(post("/portal/profile/contact-consent").session(session).with(csrf())
                        .param("phoneConsent", "true").param("emailConsent", ""))
                .andExpect(flash().attribute("message", "Tus recordatorios quedaron guardados."));
        mockMvc.perform(get("/api/portal/profile").with(as(patient)))
                .andExpect(jsonPath("$.phone.consent").value("PATIENT"))
                .andExpect(jsonPath("$.email.consent").value("OWNER"));

        mockMvc.perform(post("/portal/profile/details").session(session).with(csrf()))
                .andExpect(flash().attribute("message", "Tus datos quedaron guardados."));
        mockMvc.perform(post("/portal/profile/contact-consent").session(session).with(csrf())
                        .param("phoneConsent", "true"))
                .andExpect(flash().attribute("error", containsString("primero cargalo en tus datos")));
    }
}
