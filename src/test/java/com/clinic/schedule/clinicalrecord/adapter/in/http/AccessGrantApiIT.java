package com.clinic.schedule.clinicalrecord.adapter.in.http;

import static org.hamcrest.Matchers.contains;
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
import com.clinic.schedule.arrival.ArrivalFixture;
import com.clinic.schedule.clinicalrecord.PractitionerUserFixture;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Asking for access to a record to report a pending check, over the real stack (D-54): the
 * analyst who never saw the patient finds the check in the list of pending results, asks, is
 * granted access at once, and reports it. "Today" is 10 March 2026.
 *
 * <p>The list of pending results is the whole practice's, shared with other tests, so each test
 * looks for its own check in it.
 */
@SpringBootTest
@AutoConfigureMockMvc
@WithMockUser(roles = "RECEPTION")
class AccessGrantApiIT {

    private static final String TODAY = "2026-03-10";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper json;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private UserDetailsService userDetails;

    private RequestPostProcessor asArias;
    private RequestPostProcessor asAnalyst;
    private UUID patient;

    @BeforeEach
    void setUp() throws Exception {
        PractitionerFixture practitioners = new PractitionerFixture(mockMvc, json);
        PractitionerUserFixture users = new PractitionerUserFixture(jdbc, userDetails);
        CreatedPractitioner arias = practitioners.workingEveryDay();
        asArias = users.userFor(arias.id());
        asAnalyst = users.userFor(practitioners.workingEveryDay().id());
        patient = new PatientFixture(mockMvc, json).newPatient();

        MvcResult booked = mockMvc.perform(post("/api/appointments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"patientId": "%s", "practitionerId": "%s", "reasonId": "%s", "startsAt": "%sT10:00:00"}
                                """.formatted(patient, arias.id(), arias.consulta(), TODAY)))
                .andExpect(status().isCreated())
                .andReturn();
        new ArrivalFixture(mockMvc, jdbc).arrivedAndCalled(
                json.readTree(booked.getResponse().getContentAsString()).get("id").asText(), asArias);
    }

    private ResultActions send(RequestPostProcessor as, String path, String body) throws Exception {
        return mockMvc.perform(post(path).with(as).contentType(MediaType.APPLICATION_JSON).content(body));
    }

    /** Arias draws a blood sample and leaves its result pending. */
    private String pendingCheck() throws Exception {
        MvcResult result = send(asArias, "/api/checks", """
                {"patientId": "%s", "description": "Extracción de sangre para hemograma"}
                """.formatted(patient))
                .andExpect(status().isCreated())
                .andReturn();
        return json.readTree(result.getResponse().getContentAsString()).get("id").asText();
    }

    private String row(String check) {
        return "$[?(@.checkId == '" + check + "')]";
    }

    @Test
    void theAnalystFindsTheCheckAsksForAccessAndReportsIt() throws Exception {
        String check = pendingCheck();

        mockMvc.perform(get("/api/checks/pending").with(asAnalyst))
                .andExpect(status().isOk())
                .andExpect(jsonPath(row(check) + ".description", contains("Extracción de sangre para hemograma")))
                .andExpect(jsonPath(row(check) + ".patientId", contains(patient.toString())))
                .andExpect(jsonPath(row(check) + ".mayRead", contains(false)));
        mockMvc.perform(get("/api/clinical-records/" + patient).with(asAnalyst)).andExpect(status().isForbidden());

        send(asAnalyst, "/api/checks/" + check + "/access-grants", """
                {"reason": "Informar el hemograma"}
                """)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.patientId").value(patient.toString()))
                .andExpect(jsonPath("$.checkId").value(check))
                .andExpect(jsonPath("$.validUntil").value("2027-03-10"))
                .andExpect(jsonPath("$.approval").value("AUTOMATIC"));

        mockMvc.perform(get("/api/checks/pending").with(asAnalyst))
                .andExpect(jsonPath(row(check) + ".mayRead", contains(true)));
        send(asAnalyst, "/api/checks/" + check + "/result", """
                {"text": "Hemoglobina 13,5 g/dl"}
                """).andExpect(status().isOk());

        mockMvc.perform(get("/api/clinical-records/" + patient).with(asArias))
                .andExpect(jsonPath("$.accessGrants", hasSize(1)))
                .andExpect(jsonPath("$.accessGrants[0].reason").value("Informar el hemograma"))
                .andExpect(jsonPath("$.checks[0].result.text").value("Hemoglobina 13,5 g/dl"));
        mockMvc.perform(get("/api/checks/pending").with(asAnalyst)).andExpect(jsonPath(row(check), empty()));
    }

    @Test
    void aGrantOpensTheRecordButDoesNotLetTheAnalystRecordAConsultation() throws Exception {
        String check = pendingCheck();
        send(asAnalyst, "/api/checks/" + check + "/access-grants", """
                {"reason": "Informar"}
                """).andExpect(status().isCreated());

        send(asAnalyst, "/api/consultations", """
                {"patientId": "%s", "notes": "No lo atendí."}
                """.formatted(patient))
                .andExpect(status().isForbidden());
    }

    @Test
    void accessIsAskedOnlyForAPendingCheckAndWithAReason() throws Exception {
        String check = pendingCheck();

        send(asAnalyst, "/api/checks/" + check + "/access-grants", "{}").andExpect(status().isBadRequest());
        send(asAnalyst, "/api/checks/" + UUID.randomUUID() + "/access-grants", """
                {"reason": "Informar"}
                """).andExpect(status().isNotFound());

        send(asArias, "/api/checks/" + check + "/result", """
                {"text": "Normal."}
                """).andExpect(status().isOk());
        send(asAnalyst, "/api/checks/" + check + "/access-grants", """
                {"reason": "Informar"}
                """).andExpect(status().isConflict());
        mockMvc.perform(get("/api/clinical-records/" + patient).with(asAnalyst)).andExpect(status().isForbidden());
    }

    @Test
    void receptionCannotSeeThePendingResultsNorAskForAccess() throws Exception {
        String check = pendingCheck();

        mockMvc.perform(get("/api/checks/pending")).andExpect(status().isForbidden());
        mockMvc.perform(post("/api/checks/" + check + "/access-grants")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"reason": "Recepción"}
                                """))
                .andExpect(status().isForbidden());
    }
}
