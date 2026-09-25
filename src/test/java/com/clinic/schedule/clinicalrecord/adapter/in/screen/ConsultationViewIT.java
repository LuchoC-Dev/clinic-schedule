package com.clinic.schedule.clinicalrecord.adapter.in.screen;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import com.clinic.schedule.appointment.PatientFixture;
import com.clinic.schedule.appointment.PractitionerFixture;
import com.clinic.schedule.appointment.PractitionerFixture.CreatedPractitioner;
import com.clinic.schedule.clinicalrecord.PractitionerUserFixture;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * The practitioner's consultation screens over the real stack (D-51): the agenda leads to them,
 * the form records a consultation, and a refusal comes back in Spanish with what was typed.
 */
@SpringBootTest
@AutoConfigureMockMvc
@WithMockUser(roles = "RECEPTION")
class ConsultationViewIT {

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
    private UUID patient;

    @BeforeEach
    void setUp() throws Exception {
        arias = new PractitionerFixture(mockMvc, json).workingEveryDay();
        asArias = new PractitionerUserFixture(jdbc, userDetails).userFor(arias.id());
        patient = new PatientFixture(mockMvc, json).newPatient();
    }

    private String bookToday(String time) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/appointments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"patientId": "%s", "practitionerId": "%s", "reasonId": "%s", "startsAt": "%sT%s"}
                                """.formatted(patient, arias.id(), arias.consulta(), TODAY, time)))
                .andExpect(status().isCreated())
                .andReturn();
        return json.readTree(result.getResponse().getContentAsString()).get("id").asText();
    }

    private void arrivedToday() throws Exception {
        String appointment = bookToday("10:00:00");
        mockMvc.perform(post("/api/appointments/" + appointment + "/check-in")).andExpect(status().isOk());
    }

    /** The form as a practitioner sends it: five rows of items, some of them empty. */
    private MockHttpServletRequestBuilder form(String notes, String... kindThenDescription) {
        MockHttpServletRequestBuilder request = post("/consultations").with(asArias).with(csrf())
                .param("patientId", patient.toString())
                .param("notes", notes);
        for (int row = 0; row < 5; row++) {
            int at = row * 2;
            request.param("kind", at < kindThenDescription.length ? kindThenDescription[at] : "");
            request.param("description", at + 1 < kindThenDescription.length ? kindThenDescription[at + 1] : "");
        }
        return request;
    }

    @Test
    void theAgendaOffersTheConsultationOnceThePatientArrived() throws Exception {
        bookToday("10:00:00");

        mockMvc.perform(get("/agenda").with(asArias).param("date", TODAY))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("/clinical-records/" + patient)))
                .andExpect(content().string(not(containsString("Empezar consulta"))));

        String appointment = bookToday("11:00:00");
        mockMvc.perform(post("/api/appointments/" + appointment + "/check-in")).andExpect(status().isOk());

        mockMvc.perform(get("/agenda").with(asArias).param("date", TODAY))
                .andExpect(content().string(containsString("Empezar consulta")))
                .andExpect(content().string(containsString("/consultations/new?patientId=" + patient)));
    }

    @Test
    void aConsultationIsRecordedFromTheFormAndShownInTheRecord() throws Exception {
        arrivedToday();

        mockMvc.perform(get("/consultations/new").with(asArias).param("patientId", patient.toString()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Registrar consulta")))
                .andExpect(content().string(containsString("Todavía no hay nada registrado.")));

        mockMvc.perform(form("Control de hipertensión.", "PRESCRIPTION", "enalapril 10 mg", "", "",
                        "PRESCRIPTION", "amlodipina 5 mg")
                        .param("orderDescription", "Ecodoppler de vasos de cuello", "", ""))
                .andExpect(redirectedUrl("/clinical-records/" + patient))
                .andExpect(flash().attribute("message", "Consulta registrada."));

        mockMvc.perform(get("/clinical-records/" + patient).with(asArias))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Control de hipertensión.")))
                .andExpect(content().string(containsString("Receta:")))
                .andExpect(content().string(containsString("amlodipina 5 mg")))
                .andExpect(content().string(containsString("Orden:")))
                .andExpect(content().string(containsString("Ecodoppler de vasos de cuello")))
                .andExpect(content().string(containsString("Abierta")));
    }

    @Test
    void aHalfFilledRowIsRefusedKeepingWhatWasTyped() throws Exception {
        arrivedToday();

        mockMvc.perform(form("Control.", "PRESCRIPTION", "ibuprofeno 400 mg", "PRESCRIPTION", ""))
                .andExpect(redirectedUrl("/consultations/new?patientId=" + patient))
                .andExpect(flash().attribute("error", containsString("Cada ítem necesita su tipo y su texto")))
                .andExpect(flash().attributeExists("form"));

        mockMvc.perform(get("/clinical-records/" + patient).with(asArias))
                .andExpect(content().string(containsString("Todavía no hay nada registrado.")));
    }

    @Test
    void aConsultationWithoutNotesIsRefused() throws Exception {
        arrivedToday();

        mockMvc.perform(form("  ", "PRESCRIPTION", "ibuprofeno"))
                .andExpect(flash().attribute("error", "Escribí lo consultado."));
    }

    @Test
    void moreRowsBringTheFormBackWithoutRecordingIt() throws Exception {
        arrivedToday();

        MvcResult more = mockMvc.perform(form("Control.", "PRESCRIPTION", "ibuprofeno 400 mg").param("action", "more"))
                .andExpect(redirectedUrl("/consultations/new?patientId=" + patient))
                .andExpect(flash().attributeExists("form"))
                .andReturn();

        // The five rows sent come back as eight, with what was typed in them.
        String page = mockMvc.perform(get("/consultations/new").with(asArias).param("patientId", patient.toString())
                        .flashAttrs(more.getFlashMap()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("ibuprofeno 400 mg")))
                .andReturn().getResponse().getContentAsString();
        assertThat(page.split("name=\"kind\"", -1)).hasSize(8 + 1);

        mockMvc.perform(get("/clinical-records/" + patient).with(asArias))
                .andExpect(content().string(containsString("Todavía no hay nada registrado.")));
    }

    @Test
    void beforeThePatientArrivesTheFormFallsBackToTheRecord() throws Exception {
        bookToday("10:00:00");

        mockMvc.perform(get("/consultations/new").with(asArias).param("patientId", patient.toString()))
                .andExpect(redirectedUrl("/clinical-records/" + patient))
                .andExpect(flash().attribute("error", containsString("cuando el paciente llegó")));
        mockMvc.perform(get("/clinical-records/" + patient).with(asArias))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString("Empezar consulta"))));
    }

    @Test
    void aRecordThePractitionerMayNotReadIsForbidden() throws Exception {
        mockMvc.perform(get("/clinical-records/" + patient).with(asArias)).andExpect(status().isForbidden());
        mockMvc.perform(get("/consultations/new").with(asArias).param("patientId", patient.toString()))
                .andExpect(status().isForbidden());
        mockMvc.perform(form("Sin turno.")).andExpect(status().isForbidden());
    }

    @Test
    void receptionCannotReachTheConsultationScreens() throws Exception {
        arrivedToday();

        mockMvc.perform(get("/clinical-records/" + patient)).andExpect(status().isForbidden());
        mockMvc.perform(get("/consultations/new").param("patientId", patient.toString()))
                .andExpect(status().isForbidden());
    }

    // --- addenda (D-52) -----------------------------------------------------------------

    private String recordedConsultation() throws Exception {
        arrivedToday();
        mockMvc.perform(form("Control de hipertensión.", "PRESCRIPTION", "enalapril 10 mg"))
                .andExpect(redirectedUrl("/clinical-records/" + patient));
        MvcResult record = mockMvc.perform(get("/api/clinical-records/" + patient).with(asArias)).andReturn();
        return json.readTree(record.getResponse().getContentAsString()).get("consultations").get(0).get("id").asText();
    }

    @Test
    void anAddendumIsAddedFromTheRecordAndShownUnderItsConsultation() throws Exception {
        String consultation = recordedConsultation();

        mockMvc.perform(get("/clinical-records/" + patient).with(asArias))
                .andExpect(content().string(containsString("Agregar aclaración")));

        mockMvc.perform(post("/consultations/" + consultation + "/addenda").with(asArias).with(csrf())
                        .param("patientId", patient.toString())
                        .param("text", "La dosis es 5 mg."))
                .andExpect(redirectedUrl("/clinical-records/" + patient))
                .andExpect(flash().attribute("message", "Aclaración agregada."));

        mockMvc.perform(get("/clinical-records/" + patient).with(asArias))
                .andExpect(content().string(containsString("Aclaración —")))
                .andExpect(content().string(containsString("La dosis es 5 mg.")))
                .andExpect(content().string(containsString("enalapril 10 mg")));
    }

    @Test
    void anEmptyAddendumIsRefusedNextToItsConsultation() throws Exception {
        String consultation = recordedConsultation();

        mockMvc.perform(post("/consultations/" + consultation + "/addenda").with(asArias).with(csrf())
                        .param("patientId", patient.toString())
                        .param("text", "  "))
                .andExpect(redirectedUrl("/clinical-records/" + patient))
                .andExpect(flash().attribute("error", "Escribí la aclaración."))
                .andExpect(flash().attribute("addendumFor", UUID.fromString(consultation)));
    }

    @Test
    void anAddendumToARecordThePractitionerMayNotReadIsForbidden() throws Exception {
        String consultation = recordedConsultation();
        CreatedPractitioner benitez = new PractitionerFixture(mockMvc, json).workingEveryDay();

        mockMvc.perform(post("/consultations/" + consultation + "/addenda")
                        .with(new PractitionerUserFixture(jdbc, userDetails).userFor(benitez.id())).with(csrf())
                        .param("patientId", patient.toString())
                        .param("text", "No debería."))
                .andExpect(status().isForbidden());
    }

    // --- checks (D-53) ------------------------------------------------------------------

    @Test
    void aPendingCheckOfTheConsultationGetsItsResultFromTheRecord() throws Exception {
        arrivedToday();
        mockMvc.perform(form("Control.")
                        .param("checkDescription", "Pulso", "Extracción de sangre para hemograma", "")
                        .param("checkResult", "72 lpm", "", ""))
                .andExpect(redirectedUrl("/clinical-records/" + patient));

        mockMvc.perform(get("/clinical-records/" + patient).with(asArias))
                .andExpect(content().string(containsString("72 lpm")))
                .andExpect(content().string(containsString("Resultado pendiente.")))
                .andExpect(content().string(containsString("Cargar resultado")));

        MvcResult record = mockMvc.perform(get("/api/clinical-records/" + patient).with(asArias)).andReturn();
        String pending = json.readTree(record.getResponse().getContentAsString())
                .get("consultations").get(0).get("checks").get(1).get("id").asText();

        mockMvc.perform(post("/checks/" + pending + "/result").with(asArias).with(csrf())
                        .param("patientId", patient.toString())
                        .param("text", "Hemoglobina 13,5 g/dl"))
                .andExpect(redirectedUrl("/clinical-records/" + patient))
                .andExpect(flash().attribute("message", "Resultado cargado."));

        mockMvc.perform(get("/clinical-records/" + patient).with(asArias))
                .andExpect(content().string(containsString("Hemoglobina 13,5 g/dl")))
                .andExpect(content().string(not(containsString("Resultado pendiente."))));
    }

    @Test
    void aCheckResultWithoutWhatWasDoneIsRefused() throws Exception {
        arrivedToday();

        mockMvc.perform(form("Control.").param("checkDescription", "").param("checkResult", "72 lpm"))
                .andExpect(redirectedUrl("/consultations/new?patientId=" + patient))
                .andExpect(flash().attribute("error", containsString("cada chequeo qué se hizo")));
    }

    @Test
    void aCheckWithoutAConsultationIsRecordedFromTheRecord() throws Exception {
        arrivedToday();

        mockMvc.perform(post("/checks").with(asArias).with(csrf())
                        .param("patientId", patient.toString())
                        .param("description", "Electrocardiograma")
                        .param("result", ""))
                .andExpect(redirectedUrl("/clinical-records/" + patient))
                .andExpect(flash().attribute("message", "Chequeo registrado."));

        mockMvc.perform(get("/clinical-records/" + patient).with(asArias))
                .andExpect(content().string(containsString("Electrocardiograma")))
                .andExpect(content().string(containsString("Resultado pendiente.")));
    }

    // --- orders (D-55) ------------------------------------------------------------------

    @Test
    void anOrderIsFulfilledFromTheRecordAndItsAuthorMayCancelAnother() throws Exception {
        arrivedToday();
        mockMvc.perform(form("Control.").param("orderDescription", "Hemograma completo", "Radiografía de tórax"))
                .andExpect(redirectedUrl("/clinical-records/" + patient));
        MvcResult record = mockMvc.perform(get("/api/clinical-records/" + patient).with(asArias)).andReturn();
        String blood = json.readTree(record.getResponse().getContentAsString())
                .get("consultations").get(0).get("orders").get(0).get("id").asText();
        String xray = json.readTree(record.getResponse().getContentAsString())
                .get("consultations").get(0).get("orders").get(1).get("id").asText();

        mockMvc.perform(get("/clinical-records/" + patient).with(asArias))
                .andExpect(content().string(containsString("Cumple la orden")))
                .andExpect(content().string(containsString("Anular orden")));

        mockMvc.perform(post("/checks").with(asArias).with(csrf())
                        .param("patientId", patient.toString())
                        .param("description", "")
                        .param("orderId", blood))
                .andExpect(flash().attribute("message", "Chequeo registrado."));
        mockMvc.perform(post("/orders/" + xray + "/cancel").with(asArias).with(csrf())
                        .param("patientId", patient.toString())
                        .param("reason", "Trajo una reciente"))
                .andExpect(flash().attribute("message", "Orden anulada."));

        mockMvc.perform(get("/clinical-records/" + patient).with(asArias))
                .andExpect(content().string(containsString("Cumplida el")))
                .andExpect(content().string(containsString("Anulada: Trajo una reciente")))
                .andExpect(content().string(not(containsString("Anular orden"))));
    }

    @Test
    void cancellingWithoutAReasonIsRefused() throws Exception {
        arrivedToday();
        mockMvc.perform(form("Control.").param("orderDescription", "Hemograma completo"));
        MvcResult record = mockMvc.perform(get("/api/clinical-records/" + patient).with(asArias)).andReturn();
        String order = json.readTree(record.getResponse().getContentAsString())
                .get("consultations").get(0).get("orders").get(0).get("id").asText();

        mockMvc.perform(post("/orders/" + order + "/cancel").with(asArias).with(csrf())
                        .param("patientId", patient.toString())
                        .param("reason", " "))
                .andExpect(flash().attribute("error", "Escribí el motivo para anular la orden."));
    }
}
