package com.clinic.schedule.patient;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

/** Reception's patient screen over the real stack. */
@SpringBootTest
@AutoConfigureMockMvc
@WithMockUser(roles = "RECEPTION")
class PatientViewTest {

    @Autowired
    private MockMvc mockMvc;

    private static String unique(String name) {
        return name + " " + UUID.randomUUID().toString().substring(0, 6);
    }

    @Test
    void registeringFromTheScreenShowsThePatient() throws Exception {
        String name = unique("Pantalla");

        mockMvc.perform(post("/patients").with(csrf())
                        .param("fullName", name)
                        .param("documentType", "DNI")
                        .param("documentNumber", "40.111.222"))
                .andExpect(flash().attribute("message", "Paciente registrado: " + name + "."));

        mockMvc.perform(get("/patients").param("query", name))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(name)))
                .andExpect(content().string(containsString("DNI 40111222")));
    }

    @Test
    void aPatientWithoutADocumentIsRegistered() throws Exception {
        mockMvc.perform(post("/patients").with(csrf()).param("fullName", unique("Sin Documento")).param("documentType", ""))
                .andExpect(flash().attribute("message", startsWith("Paciente registrado")));
    }

    @Test
    void aRepeatedDocumentComesBackAsAMessage() throws Exception {
        mockMvc.perform(post("/patients").with(csrf()).param("fullName", unique("Primero"))
                .param("documentType", "DNI").param("documentNumber", "40222333"));

        mockMvc.perform(post("/patients").with(csrf()).param("fullName", unique("Segundo"))
                        .param("documentType", "DNI").param("documentNumber", "40222333"))
                .andExpect(redirectedUrl("/patients"))
                .andExpect(flash().attribute("error", "Ese documento ya pertenece a otro paciente."));
    }

    @Test
    void halfADocumentComesBackAsAMessage() throws Exception {
        mockMvc.perform(post("/patients").with(csrf()).param("fullName", unique("Medio")).param("documentNumber", "123"))
                .andExpect(flash().attribute("error", containsString("tipo y número")));
    }

    // --- protections (D-38) -------------------------------------------------------------

    @Test
    void aTooLongNameComesBackAsAMessageInsteadOfFailing() throws Exception {
        mockMvc.perform(post("/patients").with(csrf()).param("fullName", "x".repeat(600)))
                .andExpect(redirectedUrl("/patients"))
                .andExpect(flash().attribute("error", startsWith("Revisá los datos")));
    }

    @Test
    void aTooLongDocumentNumberComesBackAsAMessageInsteadOfFailing() throws Exception {
        mockMvc.perform(post("/patients").with(csrf()).param("fullName", unique("Pasaporte Largo"))
                        .param("documentType", "PASSPORT").param("documentNumber", "A".repeat(40)))
                .andExpect(redirectedUrl("/patients"))
                .andExpect(flash().attribute("error", startsWith("Revisá los datos")));
    }

    @Test
    void aShortDniComesBackAsAMessage() throws Exception {
        mockMvc.perform(post("/patients").with(csrf()).param("fullName", unique("Dni Corto"))
                        .param("documentType", "DNI").param("documentNumber", "544"))
                .andExpect(flash().attribute("error", containsString("el DNI, 7 u 8 dígitos")));
    }
}
