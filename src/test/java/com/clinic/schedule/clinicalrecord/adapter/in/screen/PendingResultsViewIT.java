package com.clinic.schedule.clinicalrecord.adapter.in.screen;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import com.clinic.schedule.appointment.PatientFixture;
import com.clinic.schedule.appointment.PractitionerFixture;
import com.clinic.schedule.appointment.PractitionerFixture.CreatedPractitioner;
import com.clinic.schedule.clinicalrecord.PractitionerUserFixture;
import com.fasterxml.jackson.databind.ObjectMapper;

/** The list of pending results and asking for access from it, over the real stack (D-54). */
@SpringBootTest
@AutoConfigureMockMvc
@WithMockUser(roles = "RECEPTION")
class PendingResultsViewIT {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper json;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private UserDetailsService userDetails;

    private RequestPostProcessor asAnalyst;
    private UUID patient;
    private String check;

    @BeforeEach
    void setUp() throws Exception {
        PractitionerFixture practitioners = new PractitionerFixture(mockMvc, json);
        PractitionerUserFixture users = new PractitionerUserFixture(jdbc, userDetails);
        CreatedPractitioner arias = practitioners.workingEveryDay();
        RequestPostProcessor asArias = users.userFor(arias.id());
        asAnalyst = users.userFor(practitioners.workingEveryDay().id());
        patient = new PatientFixture(mockMvc, json).newPatient();

        MvcResult booked = mockMvc.perform(post("/api/appointments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"patientId": "%s", "practitionerId": "%s", "reasonId": "%s", "startsAt": "2026-03-10T10:00:00"}
                                """.formatted(patient, arias.id(), arias.consulta())))
                .andExpect(status().isCreated())
                .andReturn();
        mockMvc.perform(post("/api/appointments/" + json.readTree(booked.getResponse().getContentAsString())
                .get("id").asText() + "/check-in")).andExpect(status().isOk());
        MvcResult recorded = mockMvc.perform(post("/api/checks").with(asArias)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"patientId": "%s", "description": "Extracción de sangre para glucemia"}
                                """.formatted(patient)))
                .andExpect(status().isCreated())
                .andReturn();
        check = json.readTree(recorded.getResponse().getContentAsString()).get("id").asText();
    }

    @Test
    void theAnalystAsksForAccessFromTheListAndLandsOnTheRecord() throws Exception {
        mockMvc.perform(get("/checks/pending").with(asAnalyst))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Extracción de sangre para glucemia")))
                .andExpect(content().string(containsString("/checks/" + check + "/access-grants")));

        mockMvc.perform(post("/checks/" + check + "/access-grants").with(asAnalyst).with(csrf())
                        .param("reason", "Informar la glucemia"))
                .andExpect(redirectedUrl("/clinical-records/" + patient))
                .andExpect(flash().attribute("message", "Acceso autorizado hasta el 10/03/2027."));

        mockMvc.perform(get("/clinical-records/" + patient).with(asAnalyst))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Accesos autorizados")))
                .andExpect(content().string(containsString("Informar la glucemia")))
                .andExpect(content().string(containsString("Cargar resultado")));
    }

    @Test
    void askingWithoutAReasonComesBackToTheList() throws Exception {
        mockMvc.perform(post("/checks/" + check + "/access-grants").with(asAnalyst).with(csrf()).param("reason", " "))
                .andExpect(redirectedUrl("/checks/pending"))
                .andExpect(flash().attribute("error", "Escribí el motivo del pedido de acceso."));

        mockMvc.perform(get("/clinical-records/" + patient).with(asAnalyst)).andExpect(status().isForbidden());
    }

    @Test
    void receptionCannotReachTheList() throws Exception {
        mockMvc.perform(get("/checks/pending")).andExpect(status().isForbidden());
    }
}
