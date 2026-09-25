package com.clinic.schedule.clinicalrecord.adapter.in.http;

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

/**
 * Checks over the real stack (D-53): done during a consultation or without one, with their result
 * at once or later, maybe from someone else. Reception, the default user, books and checks
 * patients in; each practitioner acts through a user of their own. "Today" is 10 March 2026.
 */
@SpringBootTest
@AutoConfigureMockMvc
@WithMockUser(roles = "RECEPTION")
class CheckApiIT {

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
    private PractitionerUserFixture users;
    private CreatedPractitioner arias;
    private RequestPostProcessor asArias;
    private UUID patient;

    @BeforeEach
    void setUp() throws Exception {
        practitioners = new PractitionerFixture(mockMvc, json);
        users = new PractitionerUserFixture(jdbc, userDetails);
        arias = practitioners.workingEveryDay();
        asArias = users.userFor(arias.id());
        patient = new PatientFixture(mockMvc, json).newPatient();
    }

    private String book(CreatedPractitioner practitioner, String time) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/appointments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"patientId": "%s", "practitionerId": "%s", "reasonId": "%s", "startsAt": "%sT%s"}
                                """.formatted(patient, practitioner.id(), practitioner.consulta(), TODAY, time)))
                .andExpect(status().isCreated())
                .andReturn();
        return idOf(result);
    }

    private String arrivedToday() throws Exception {
        String appointment = book(arias, "10:00:00");
        mockMvc.perform(post("/api/appointments/" + appointment + "/check-in")).andExpect(status().isOk());
        return appointment;
    }

    private String idOf(MvcResult result) throws Exception {
        return json.readTree(result.getResponse().getContentAsString()).get("id").asText();
    }

    private ResultActions send(RequestPostProcessor as, String path, String body) throws Exception {
        return mockMvc.perform(post(path).with(as).contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private ResultActions readRecord(RequestPostProcessor as) throws Exception {
        return mockMvc.perform(get("/api/clinical-records/" + patient).with(as));
    }

    /** A consultation with two checks: a pulse with its result, and a blood sample whose result is pending. */
    private String consultationWithAPendingCheck() throws Exception {
        arrivedToday();
        MvcResult result = send(asArias, "/api/consultations", """
                {"patientId": "%s", "notes": "Control.", "checks": [
                  {"description": "Pulso", "result": "72 lpm"},
                  {"description": "Extracción de sangre para hemograma"}]}
                """.formatted(patient))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.checks", hasSize(2)))
                .andExpect(jsonPath("$.checks[0].pending").value(false))
                .andExpect(jsonPath("$.checks[0].result.text").value("72 lpm"))
                .andExpect(jsonPath("$.checks[1].pending").value(true))
                .andReturn();
        return json.readTree(result.getResponse().getContentAsString()).get("checks").get(1).get("id").asText();
    }

    @Test
    void theChecksOfAConsultationComeWithItInTheRecord() throws Exception {
        consultationWithAPendingCheck();

        readRecord(asArias)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.consultations[0].checks", hasSize(2)))
                .andExpect(jsonPath("$.consultations[0].checks[1].result").doesNotExist())
                .andExpect(jsonPath("$.checks", hasSize(0)));
    }

    @Test
    void anotherPractitionerWithAccessRecordsThePendingResultOnce() throws Exception {
        String check = consultationWithAPendingCheck();
        CreatedPractitioner analyst = practitioners.workingEveryDay();
        RequestPostProcessor asAnalyst = users.userFor(analyst.id());
        String result = """
                {"text": "Hemoglobina 13,5 g/dl"}
                """;

        // Without access to the record yet: the authorization of part 3b is what will grant it.
        send(asAnalyst, "/api/checks/" + check + "/result", result).andExpect(status().isForbidden());

        book(analyst, "11:00:00");
        send(asAnalyst, "/api/checks/" + check + "/result", result)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pending").value(false))
                .andExpect(jsonPath("$.performedBy").value(arias.id().toString()))
                .andExpect(jsonPath("$.result.authorId").value(analyst.id().toString()));

        send(asArias, "/api/checks/" + check + "/result", """
                {"text": "Otro resultado."}
                """)
                .andExpect(status().isConflict());
        send(asAnalyst, "/api/checks/" + check + "/result", result).andExpect(status().isOk());

        readRecord(asArias).andExpect(jsonPath("$.consultations[0].checks[1].result.text").value("Hemoglobina 13,5 g/dl"));
    }

    @Test
    void aCheckWithoutAConsultationNeedsThePatientToHaveArrived() throws Exception {
        String appointment = book(arias, "10:00:00");
        String body = """
                {"patientId": "%s", "description": "Electrocardiograma"}
                """.formatted(patient);

        send(asArias, "/api/checks", body).andExpect(status().isForbidden());

        mockMvc.perform(post("/api/appointments/" + appointment + "/check-in")).andExpect(status().isOk());
        send(asArias, "/api/checks", body)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.consultationId").doesNotExist())
                .andExpect(jsonPath("$.pending").value(true));

        readRecord(asArias)
                .andExpect(jsonPath("$.consultations", hasSize(0)))
                .andExpect(jsonPath("$.checks", hasSize(1)))
                .andExpect(jsonPath("$.checks[0].description").value("Electrocardiograma"));
    }

    @Test
    void doingACheckKeepsTheRecordOpenLikeAConsultation() throws Exception {
        String appointment = arrivedToday();
        send(asArias, "/api/checks", """
                {"patientId": "%s", "description": "Presión arterial", "result": "120/80"}
                """.formatted(patient)).andExpect(status().isCreated());

        mockMvc.perform(post("/api/appointments/" + appointment + "/cancel")).andExpect(status().isOk());

        readRecord(asArias).andExpect(status().isOk()).andExpect(jsonPath("$.checks", hasSize(1)));
    }

    @Test
    void anUnknownCheckIsForbiddenAndReceptionCannotReachChecks() throws Exception {
        String check = consultationWithAPendingCheck();

        send(asArias, "/api/checks/" + UUID.randomUUID() + "/result", """
                {"text": "x"}
                """).andExpect(status().isForbidden());
        mockMvc.perform(post("/api/checks/" + check + "/result")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"text": "Recepción no escribe."}
                                """))
                .andExpect(status().isForbidden());
    }

    @Test
    void aCheckIsNotAnItemAnyMore() throws Exception {
        arrivedToday();

        send(asArias, "/api/consultations", """
                {"patientId": "%s", "notes": "Control.", "items": [{"kind": "CHECK", "description": "pulso 72"}]}
                """.formatted(patient))
                .andExpect(status().isBadRequest());
        send(asArias, "/api/consultations", """
                {"patientId": "%s", "notes": "Control.", "checks": [{"description": " ", "result": "72"}]}
                """.formatted(patient))
                .andExpect(status().isBadRequest());
    }

    // --- addenda (D-58) -----------------------------------------------------------------

    @Test
    void aWrongResultIsCorrectedWithAnAddendumByWhoeverMayReadTheRecord() throws Exception {
        String check = consultationWithAPendingCheck();
        send(asArias, "/api/checks/" + check + "/result", """
                {"text": "Resultado equivocado."}
                """).andExpect(status().isOk());
        CreatedPractitioner other = practitioners.workingEveryDay();
        RequestPostProcessor asOther = users.userFor(other.id());
        String body = """
                {"id": "%s", "text": "El resultado corresponde a otro paciente."}
                """.formatted(UUID.randomUUID());

        send(asOther, "/api/checks/" + check + "/addenda", body).andExpect(status().isForbidden());

        send(asArias, "/api/checks/" + check + "/addenda", body)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.result.text").value("Resultado equivocado."))
                .andExpect(jsonPath("$.addenda", hasSize(1)))
                .andExpect(jsonPath("$.addenda[0].practitionerId").value(arias.id().toString()));
        send(asArias, "/api/checks/" + check + "/addenda", body)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.addenda", hasSize(1)));

        readRecord(asArias)
                .andExpect(jsonPath("$.consultations[0].checks[1].addenda[0].text")
                        .value("El resultado corresponde a otro paciente."));
    }

    @Test
    void anAddendumNeedsATextAndAnExistingCheck() throws Exception {
        String check = consultationWithAPendingCheck();

        send(asArias, "/api/checks/" + check + "/addenda", """
                {"text": " "}
                """).andExpect(status().isBadRequest());
        send(asArias, "/api/checks/" + UUID.randomUUID() + "/addenda", """
                {"text": "x"}
                """).andExpect(status().isForbidden());
    }
}
