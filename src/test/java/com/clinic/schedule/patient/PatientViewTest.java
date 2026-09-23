package com.clinic.schedule.patient;

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
import org.springframework.test.web.servlet.MockMvc;

/** Reception's patient screen over the real stack. */
@SpringBootTest
@AutoConfigureMockMvc
class PatientViewTest {

    @Autowired
    private MockMvc mockMvc;

    private static String unique(String name) {
        return name + " " + UUID.randomUUID().toString().substring(0, 6);
    }

    @Test
    void registeringFromTheScreenShowsThePatient() throws Exception {
        String name = unique("Pantalla");

        mockMvc.perform(post("/patients")
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
        mockMvc.perform(post("/patients").param("fullName", unique("Sin Documento")).param("documentType", ""))
                .andExpect(flash().attribute("message", startsWith("Paciente registrado")));
    }

    @Test
    void aRepeatedDocumentComesBackAsAMessage() throws Exception {
        mockMvc.perform(post("/patients").param("fullName", unique("Primero"))
                .param("documentType", "DNI").param("documentNumber", "40222333"));

        mockMvc.perform(post("/patients").param("fullName", unique("Segundo"))
                        .param("documentType", "DNI").param("documentNumber", "40222333"))
                .andExpect(redirectedUrl("/patients"))
                .andExpect(flash().attribute("error", "Ese documento ya pertenece a otro paciente."));
    }

    @Test
    void halfADocumentComesBackAsAMessage() throws Exception {
        mockMvc.perform(post("/patients").param("fullName", unique("Medio")).param("documentNumber", "123"))
                .andExpect(flash().attribute("error", containsString("tipo y número")));
    }
}
