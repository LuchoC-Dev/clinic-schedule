package com.clinic.schedule.clinicalrecord.adapter.in.http;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
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
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import com.clinic.schedule.appointment.PatientFixture;
import com.clinic.schedule.appointment.PractitionerFixture;
import com.clinic.schedule.appointment.PractitionerFixture.CreatedPractitioner;
import com.clinic.schedule.clinicalrecord.PractitionerUserFixture;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;

/**
 * The list the author of orders follows them up with, over the real stack (D-56): their own
 * orders, not cancelled, each with where it stands, and no result text. "Today" is 10 March 2026.
 */
@SpringBootTest
@AutoConfigureMockMvc
@WithMockUser(roles = "RECEPTION")
class MyOrdersApiIT {

    private static final String TODAY = "2026-03-10";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper json;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private UserDetailsService userDetails;

    private CreatedPractitioner arias;
    private RequestPostProcessor asArias;
    private CreatedPractitioner lab;
    private RequestPostProcessor asLab;
    private UUID patient;

    @BeforeEach
    void setUp() throws Exception {
        PractitionerFixture practitioners = new PractitionerFixture(mockMvc, json);
        PractitionerUserFixture users = new PractitionerUserFixture(jdbc, userDetails);
        arias = practitioners.workingEveryDay();
        asArias = users.userFor(arias.id());
        lab = practitioners.workingEveryDay();
        asLab = users.userFor(lab.id());
        patient = new PatientFixture(mockMvc, json).newPatient();
    }

    private void arrivesFor(CreatedPractitioner practitioner, String time) throws Exception {
        MvcResult booked = mockMvc.perform(post("/api/appointments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"patientId": "%s", "practitionerId": "%s", "reasonId": "%s", "startsAt": "%sT%s"}
                                """.formatted(patient, practitioner.id(), practitioner.consulta(), TODAY, time)))
                .andExpect(status().isCreated())
                .andReturn();
        mockMvc.perform(post("/api/appointments/" + json.readTree(booked.getResponse().getContentAsString())
                .get("id").asText() + "/check-in")).andExpect(status().isOk());
    }

    private ResultActions send(RequestPostProcessor as, String path, String body) throws Exception {
        return mockMvc.perform(post(path).with(as).contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private static String idOf(JsonNode orders, String description) {
        for (JsonNode order : orders) {
            if (order.get("description").asText().equals(description)) {
                return order.get("id").asText();
            }
        }
        throw new AssertionError("No order " + description);
    }

    @Test
    void theAuthorFollowsUpEachOrderButTheCancelledOnes() throws Exception {
        arrivesFor(arias, "10:00:00");
        MvcResult consultation = send(asArias, "/api/consultations", """
                {"patientId": "%s", "notes": "Control.", "orders": [
                  {"description": "Hemograma"}, {"description": "Glucemia"},
                  {"description": "Radiografía"}, {"description": "Ecografía"}]}
                """.formatted(patient))
                .andExpect(status().isCreated())
                .andReturn();
        JsonNode orders = json.readTree(consultation.getResponse().getContentAsString()).get("orders");

        arrivesFor(lab, "11:00:00");
        send(asLab, "/api/checks", """
                {"patientId": "%s", "orderId": "%s"}
                """.formatted(patient, idOf(orders, "Glucemia"))).andExpect(status().isCreated());
        send(asLab, "/api/checks", """
                {"patientId": "%s", "orderId": "%s", "result": "98 mg/dl"}
                """.formatted(patient, idOf(orders, "Radiografía"))).andExpect(status().isCreated());
        send(asArias, "/api/orders/" + idOf(orders, "Ecografía") + "/cancel", """
                {"reason": "No hace falta"}
                """).andExpect(status().isOk());

        mockMvc.perform(get("/api/orders/mine").with(asArias))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(3)))
                .andExpect(jsonPath("$[*].description", containsInAnyOrder("Hemograma", "Glucemia", "Radiografía")))
                .andExpect(jsonPath("$[?(@.description == 'Hemograma')].situation").value("OPEN"))
                .andExpect(jsonPath("$[?(@.description == 'Glucemia')].situation").value("AWAITING_RESULT"))
                .andExpect(jsonPath("$[?(@.description == 'Radiografía')].situation").value("REPORTED"))
                .andExpect(jsonPath("$[?(@.description == 'Radiografía')].reportedAt").value("2026-03-10T00:00:00"))
                .andExpect(jsonPath("$[?(@.description == 'Radiografía')].mayRead").value(true))
                .andExpect(jsonPath("$[0].result").doesNotExist());

        mockMvc.perform(get("/api/orders/mine").with(asLab)).andExpect(jsonPath("$", empty()));
    }

    @Test
    void receptionHasNoOrders() throws Exception {
        mockMvc.perform(get("/api/orders/mine")).andExpect(status().isForbidden());
    }
}
