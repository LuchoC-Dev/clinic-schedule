package com.clinic.schedule.clinicalrecord.adapter.in.screen;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
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
import com.clinic.schedule.arrival.ArrivalFixture;
import com.clinic.schedule.clinicalrecord.PractitionerUserFixture;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * "Mis órdenes" over the real stack (D-56): what waits, what was reported recently, and all the
 * reported ones on asking. A result older than the recent window is written straight into the
 * table, since the tests' clock cannot record one in the past.
 */
@SpringBootTest
@AutoConfigureMockMvc
@WithMockUser(roles = "RECEPTION")
class MyOrdersViewIT {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper json;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private UserDetailsService userDetails;

    private RequestPostProcessor asArias;
    private UUID patient;

    @BeforeEach
    void setUp() throws Exception {
        CreatedPractitioner arias = new PractitionerFixture(mockMvc, json).workingEveryDay();
        asArias = new PractitionerUserFixture(jdbc, userDetails).userFor(arias.id());
        patient = new PatientFixture(mockMvc, json).newPatient();
        MvcResult booked = mockMvc.perform(post("/api/appointments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"patientId": "%s", "practitionerId": "%s", "reasonId": "%s", "startsAt": "2026-03-10T10:00:00"}
                                """.formatted(patient, arias.id(), arias.consulta())))
                .andExpect(status().isCreated())
                .andReturn();
        new ArrivalFixture(mockMvc, jdbc).arrivedAndCalled(
                json.readTree(booked.getResponse().getContentAsString()).get("id").asText(), asArias);
    }

    @Test
    void waitingOrdersAndRecentResultsShowAndOlderResultsOnlyOnAsking() throws Exception {
        MvcResult consultation = mockMvc.perform(post("/api/consultations").with(asArias)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"patientId": "%s", "notes": "Control.", "orders": [
                                  {"description": "Hemograma pendiente"}, {"description": "Radiografía vieja"}]}
                                """.formatted(patient)))
                .andExpect(status().isCreated())
                .andReturn();
        String old = null;
        for (var order : json.readTree(consultation.getResponse().getContentAsString()).get("orders")) {
            if (order.get("description").asText().equals("Radiografía vieja")) {
                old = order.get("id").asText();
            }
        }
        MvcResult done = mockMvc.perform(post("/api/checks").with(asArias)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"patientId": "%s", "orderId": "%s", "result": "Sin lesiones"}
                                """.formatted(patient, old)))
                .andExpect(status().isCreated())
                .andReturn();
        String check = json.readTree(done.getResponse().getContentAsString()).get("id").asText();

        mockMvc.perform(get("/orders/mine").with(asArias))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Hemograma pendiente")))
                .andExpect(content().string(containsString("Abierta: nadie hizo el chequeo todavía")))
                .andExpect(content().string(containsString("Radiografía vieja")))
                .andExpect(content().string(not(containsString("Sin lesiones"))));

        // The result, as if it had been written two months ago.
        jdbc.update("update clinical_check set performed_at = timestamp '2026-01-05 10:00:00', "
                + "result_written_at = timestamp '2026-01-06 10:00:00' where id = ?", UUID.fromString(check));
        jdbc.update("update check_order set ordered_at = timestamp '2026-01-05 09:00:00' where id = ?",
                UUID.fromString(old));

        mockMvc.perform(get("/orders/mine").with(asArias))
                .andExpect(content().string(containsString("Hemograma pendiente")))
                .andExpect(content().string(not(containsString("Radiografía vieja"))));
        mockMvc.perform(get("/orders/mine").with(asArias).param("all", "true"))
                .andExpect(content().string(containsString("Radiografía vieja")))
                .andExpect(content().string(containsString("Resultado del mar 06/01/2026 10:00")));
    }

    @Test
    void receptionCannotReachIt() throws Exception {
        mockMvc.perform(get("/orders/mine")).andExpect(status().isForbidden());
    }
}
