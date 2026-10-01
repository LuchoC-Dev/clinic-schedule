package com.clinic.schedule.patient.presentation.screen;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import com.fasterxml.jackson.databind.ObjectMapper;

/** Consent to be reminded, on the patient's screen (D-77). */
@SpringBootTest
@AutoConfigureMockMvc
@WithMockUser(username = "recepcion", roles = "RECEPTION")
class PatientConsentViewIT {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper json;

    private String registered(String body) throws Exception {
        return json.readTree(mockMvc.perform(post("/api/patients")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString()).get("id").asText();
    }

    @Test
    void eachChannelOffersItsConsentAndItsOwnersOnlyWhenItIsAResponsiblePersons() throws Exception {
        String id = registered("""
                {"fullName": "Luis Perez", "phone": {"number": "1145556666"},
                 "email": {"address": "ana@mail.com", "owner": {"name": "Ana Perez", "relation": "MOTHER"}}}
                """);

        String screen = mockMvc.perform(get("/patients/" + id))
                .andExpect(content().string(containsString("name=\"phoneConsent\"")))
                .andExpect(content().string(containsString("name=\"emailConsent\"")))
                .andReturn().getResponse().getContentAsString();

        // The phone is his own, so only he consents to it: the owner's choice is offered once, for the email.
        assertThat(screen.split("value=\"OWNER\"", -1)).hasSize(2);
        assertThat(screen).contains("Lo dio Madre: Ana Perez");
    }

    @Test
    void aPatientWithNoChannelIsToldThereIsNowhereToRemindThem() throws Exception {
        String id = registered("""
                {"fullName": "Sin Contacto"}
                """);

        mockMvc.perform(get("/patients/" + id))
                .andExpect(content().string(containsString("no hay a dónde mandarle recordatorios")))
                .andExpect(content().string(not(containsString("name=\"phoneConsent\""))));
    }

    @Test
    void savingTheConsentShowsItChosenAndInTheHistory() throws Exception {
        String id = registered("""
                {"fullName": "Luis Perez", "phone": {"number": "1145556666"},
                 "email": {"address": "ana@mail.com", "owner": {"name": "Ana Perez", "relation": "MOTHER"}}}
                """);

        mockMvc.perform(post("/patients/" + id + "/consent").with(csrf())
                        .param("phoneConsent", "")
                        .param("emailConsent", "OWNER"))
                .andExpect(flash().attribute("message", "Consentimiento guardado."));

        mockMvc.perform(get("/patients/" + id))
                .andExpect(content().string(containsString("<option value=\"OWNER\" selected=\"selected\">Lo dio Madre: Ana Perez")))
                .andExpect(content().string(containsString("Recordatorios al email")))
                .andExpect(content().string(containsString("Lo dio la persona responsable")));
    }

    @Test
    void aConsentThatCannotBeRightIsToldOnTheScreen() throws Exception {
        String id = registered("""
                {"fullName": "Luis Perez", "phone": {"number": "1145556666"}}
                """);

        mockMvc.perform(post("/patients/" + id + "/consent").with(csrf()).param("phoneConsent", "OWNER"))
                .andExpect(flash().attribute("error", containsString("solo lo da la persona responsable")));
    }
}
