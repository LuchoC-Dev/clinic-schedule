package com.clinic.schedule.patient.presentation.http;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
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
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Consent to be reminded on each of the patient's channels, over the real stack (D-77): recorded and
 * withdrawn by reception alone, with who gave it, dropped when its channel changes, every change in the
 * history of the details. "Now" is 10 March 2026 at midnight.
 */
@SpringBootTest
@AutoConfigureMockMvc
@WithMockUser(username = "recepcion", roles = "RECEPTION")
class PatientConsentApiIT {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper json;

    @Autowired
    private JdbcTemplate jdbc;

    /** A boy with a phone of his own and his mother's email. */
    private String registered() throws Exception {
        return json.readTree(mockMvc.perform(post("/api/patients")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fullName": "Luis Perez", "phone": {"number": "1145556666"},
                                 "email": {"address": "ana@mail.com", "owner": {"name": "Ana Perez", "relation": "MOTHER"}}}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.phone.consent").value(nullValue()))
                .andExpect(jsonPath("$.email.consent").value(nullValue()))
                .andReturn().getResponse().getContentAsString()).get("id").asText();
    }

    private ResultActions consent(String id, String body) throws Exception {
        return mockMvc.perform(put("/api/patients/" + id + "/contact-consent")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    @Test
    void receptionRecordsWhoAgreedOnEachChannelAndTheHistoryKeepsIt() throws Exception {
        String id = registered();

        consent(id, """
                {"phone": "PATIENT", "email": "OWNER"}
                """)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.phone.consent").value("PATIENT"))
                .andExpect(jsonPath("$.email.consent").value("OWNER"));

        mockMvc.perform(get("/api/patients/" + id))
                .andExpect(jsonPath("$.email.consent").value("OWNER"));
        mockMvc.perform(get("/api/patients/" + id + "/details-changes"))
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[?(@.field == 'PHONE_CONSENT')].after.value").value("PATIENT"))
                .andExpect(jsonPath("$[?(@.field == 'EMAIL_CONSENT')].after.value").value("OWNER"))
                .andExpect(jsonPath("$[?(@.field == 'EMAIL_CONSENT')].changedBy").value("recepcion"));
    }

    @Test
    void leavingAChannelOutWithdrawsItsConsent() throws Exception {
        String id = registered();
        consent(id, """
                {"phone": "PATIENT", "email": "OWNER"}
                """).andExpect(status().isOk());

        consent(id, """
                {"email": "PATIENT"}
                """)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.phone.consent").value(nullValue()))
                .andExpect(jsonPath("$.email.consent").value("PATIENT"));
    }

    @Test
    void aConsentThatCannotBeRightIsRefused() throws Exception {
        String id = registered();

        // His own phone takes his consent, not an owner's.
        consent(id, """
                {"phone": "OWNER"}
                """).andExpect(status().isBadRequest());
        consent(id, """
                {"phone": "SOMEONE"}
                """).andExpect(status().isBadRequest());
        consent(UUID.randomUUID().toString(), "{}").andExpect(status().isNotFound());
    }

    @Test
    void changingTheChannelDropsItsConsent() throws Exception {
        String id = registered();
        consent(id, """
                {"phone": "PATIENT", "email": "OWNER"}
                """).andExpect(status().isOk());

        mockMvc.perform(put("/api/patients/" + id + "/details")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"phone": {"number": "1149998888"},
                                 "email": {"address": "ana@mail.com", "owner": {"name": "Ana Perez", "relation": "MOTHER"}}}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.phone.consent").value(nullValue()))
                .andExpect(jsonPath("$.email.consent").value("OWNER"));
        mockMvc.perform(get("/api/patients/" + id + "/details-changes"))
                .andExpect(jsonPath("$[?(@.field == 'PHONE_CONSENT')]", hasSize(2)));
    }

    @Test
    void theDatabaseKeepsAConsentWithItsChannel() throws Exception {
        String id = registered();

        assertThatThrownBy(() -> jdbc.update(
                "update patient set phone_consent = 'OWNER' where id = ?", UUID.fromString(id)))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update(
                "update patient set phone_consent = 'PATIENT', phone_number = null where id = ?", UUID.fromString(id)))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update(
                "update patient set email_consent = 'SOMEONE' where id = ?", UUID.fromString(id)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void onlyReceptionReachesIt() throws Exception {
        String id = registered();

        mockMvc.perform(put("/api/patients/" + id + "/contact-consent").with(user("admin").roles("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(put("/api/patients/" + id + "/contact-consent").with(user("arias").roles("PRACTITIONER"))
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isForbidden());
    }
}
