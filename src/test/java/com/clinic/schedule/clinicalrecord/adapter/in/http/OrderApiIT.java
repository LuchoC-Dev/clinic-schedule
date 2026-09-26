package com.clinic.schedule.clinicalrecord.adapter.in.http;

import static org.hamcrest.Matchers.contains;
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
import com.clinic.schedule.arrival.ArrivalFixture;
import com.clinic.schedule.clinicalrecord.PractitionerUserFixture;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Orders for a check over the real stack (D-55): issued in a consultation, fulfilled by one check
 * done by whoever does it, or cancelled by their author with a reason. "Today" is 10 March 2026.
 */
@SpringBootTest
@AutoConfigureMockMvc
@WithMockUser(roles = "RECEPTION")
class OrderApiIT {

    private static final String TODAY = "2026-03-10";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper json;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private UserDetailsService userDetails;

    private PractitionerFixture practitioners;
    private PatientFixture patients;
    private PractitionerUserFixture users;
    private CreatedPractitioner arias;
    private RequestPostProcessor asArias;
    private CreatedPractitioner lab;
    private RequestPostProcessor asLab;
    private UUID patient;

    @BeforeEach
    void setUp() throws Exception {
        practitioners = new PractitionerFixture(mockMvc, json);
        patients = new PatientFixture(mockMvc, json);
        users = new PractitionerUserFixture(jdbc, userDetails);
        arias = practitioners.workingEveryDay();
        asArias = users.userFor(arias.id());
        lab = practitioners.workingEveryDay();
        asLab = users.userFor(lab.id());
        patient = patients.newPatient();
    }

    private void arrivesFor(CreatedPractitioner practitioner, UUID who, String time) throws Exception {
        MvcResult booked = mockMvc.perform(post("/api/appointments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"patientId": "%s", "practitionerId": "%s", "reasonId": "%s", "startsAt": "%sT%s"}
                                """.formatted(who, practitioner.id(), practitioner.consulta(), TODAY, time)))
                .andExpect(status().isCreated())
                .andReturn();
        new ArrivalFixture(mockMvc, jdbc).arrivedAndCalled(
                json.readTree(booked.getResponse().getContentAsString()).get("id").asText(),
                practitioner.equals(arias) ? asArias : asLab);
    }

    private ResultActions send(RequestPostProcessor as, String path, String body) throws Exception {
        return mockMvc.perform(post(path).with(as).contentType(MediaType.APPLICATION_JSON).content(body));
    }

    /** Arias sees the patient and orders a blood test she does not do herself. */
    private String anOpenOrder() throws Exception {
        arrivesFor(arias, patient, "10:00:00");
        MvcResult result = send(asArias, "/api/consultations", """
                {"patientId": "%s", "notes": "Control.", "orders": [{"description": "Hemograma completo"}]}
                """.formatted(patient))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.orders", hasSize(1)))
                .andExpect(jsonPath("$.orders[0].status").value("OPEN"))
                .andReturn();
        return json.readTree(result.getResponse().getContentAsString()).get("orders").get(0).get("id").asText();
    }

    @Test
    void whoeverDoesTheCheckFulfilsTheOrderOnce() throws Exception {
        String order = anOpenOrder();
        arrivesFor(lab, patient, "11:00:00");

        MvcResult done = send(asLab, "/api/checks", """
                {"patientId": "%s", "orderId": "%s"}
                """.formatted(patient, order))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.description").value("Hemograma completo"))
                .andExpect(jsonPath("$.pending").value(true))
                .andReturn();
        String check = json.readTree(done.getResponse().getContentAsString()).get("id").asText();

        mockMvc.perform(get("/api/clinical-records/" + patient).with(asArias))
                .andExpect(jsonPath("$.consultations[0].orders[0].status").value("FULFILLED"))
                .andExpect(jsonPath("$.consultations[0].orders[0].fulfilledBy").value(check))
                .andExpect(jsonPath("$.checks[0].id").value(check));

        send(asLab, "/api/checks", """
                {"patientId": "%s", "description": "Otra extracción", "orderId": "%s"}
                """.formatted(patient, order))
                .andExpect(status().isConflict());
    }

    @Test
    void aCheckInALaterConsultationMayFulfilAnOrder() throws Exception {
        String order = anOpenOrder();

        send(asArias, "/api/consultations", """
                {"patientId": "%s", "notes": "Vuelve con la muestra.", "checks": [{"orderId": "%s"}]}
                """.formatted(patient, order))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.checks[0].description").value("Hemograma completo"));

        mockMvc.perform(get("/api/clinical-records/" + patient).with(asArias))
                .andExpect(jsonPath("$.consultations[*].orders[*].status", contains("FULFILLED")));
    }

    @Test
    void anOrderOfAnotherPatientIsNotFulfilled() throws Exception {
        String order = anOpenOrder();
        UUID someoneElse = patients.newPatient();
        arrivesFor(lab, someoneElse, "11:00:00");

        send(asLab, "/api/checks", """
                {"patientId": "%s", "orderId": "%s"}
                """.formatted(someoneElse, order))
                .andExpect(status().isBadRequest());
    }

    @Test
    void onlyItsAuthorCancelsItWithAReason() throws Exception {
        String order = anOpenOrder();

        send(asLab, "/api/orders/" + order + "/cancel", """
                {"reason": "No es mía"}
                """).andExpect(status().isForbidden());
        send(asArias, "/api/orders/" + order + "/cancel", "{}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields.reason").exists());
        send(asArias, "/api/orders/" + order + "/cancel", """
                {"reason": "El paciente trajo uno reciente"}
                """)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"))
                .andExpect(jsonPath("$.cancellationReason").value("El paciente trajo uno reciente"));

        send(asArias, "/api/orders/" + order + "/cancel", """
                {"reason": "Otra vez"}
                """).andExpect(status().isConflict());
        arrivesFor(lab, patient, "11:00:00");
        send(asLab, "/api/checks", """
                {"patientId": "%s", "orderId": "%s"}
                """.formatted(patient, order))
                .andExpect(status().isConflict());
    }

    @Test
    void anUnknownOrderCannotBeCancelledAndReceptionCannotReachOrders() throws Exception {
        String order = anOpenOrder();

        send(asArias, "/api/orders/" + UUID.randomUUID() + "/cancel", """
                {"reason": "x"}
                """).andExpect(status().isForbidden());
        mockMvc.perform(post("/api/orders/" + order + "/cancel")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"reason": "Recepción"}
                                """))
                .andExpect(status().isForbidden());
    }

    @Test
    void anOrderIsNotAnItemAnyMore() throws Exception {
        arrivesFor(arias, patient, "10:00:00");

        send(asArias, "/api/consultations", """
                {"patientId": "%s", "notes": "Control.", "items": [{"kind": "STUDY_ORDER", "description": "rx"}]}
                """.formatted(patient))
                .andExpect(status().isBadRequest());
    }
}
