package com.clinic.schedule.clinicalrecord.adapter.in.http;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
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
 * Every reading of a clinical record leaves a trace, with the access that allowed it (D-59): the
 * record read whole, a file attached to it, and nothing for what is refused or for the lists that
 * show no clinical content. Nothing serves the readings, so they are looked at in the table.
 * "Today" is 10 March 2026, at midnight.
 */
@SpringBootTest
@AutoConfigureMockMvc
@WithMockUser(roles = "RECEPTION")
class RecordReadingApiIT {

    private static final byte[] PDF = "%PDF-1.4\n% informe\n".getBytes(StandardCharsets.US_ASCII);

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper json;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private UserDetailsService userDetails;

    private CreatedPractitioner arias;
    private CreatedPractitioner analyst;
    private RequestPostProcessor asArias;
    private RequestPostProcessor asAnalyst;
    private UUID patient;
    private String appointment;

    @BeforeEach
    void setUp() throws Exception {
        PractitionerFixture practitioners = new PractitionerFixture(mockMvc, json);
        PractitionerUserFixture users = new PractitionerUserFixture(jdbc, userDetails);
        arias = practitioners.workingEveryDay();
        analyst = practitioners.workingEveryDay();
        asArias = users.userFor(arias.id());
        asAnalyst = users.userFor(analyst.id());
        patient = new PatientFixture(mockMvc, json).newPatient();
        appointment = idOf(mockMvc.perform(post("/api/appointments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"patientId": "%s", "practitionerId": "%s", "reasonId": "%s", "startsAt": "2026-03-10T10:00:00"}
                                """.formatted(patient, arias.id(), arias.consulta())))
                .andExpect(status().isCreated())
                .andReturn());
        mockMvc.perform(post("/api/appointments/" + appointment + "/check-in")).andExpect(status().isOk());
    }

    private String idOf(MvcResult result) throws Exception {
        return json.readTree(result.getResponse().getContentAsString()).get("id").asText();
    }

    private ResultActions send(RequestPostProcessor as, String path, String body) throws Exception {
        return mockMvc.perform(post(path).with(as).contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private String pendingCheck() throws Exception {
        return idOf(send(asArias, "/api/checks", """
                {"patientId": "%s", "description": "Extracción de sangre"}
                """.formatted(patient))
                .andExpect(status().isCreated())
                .andReturn());
    }

    private List<Map<String, Object>> readingsBy(CreatedPractitioner practitioner) {
        return jdbc.queryForList("select access, attachment_id, read_at from record_reading "
                + "where practitioner_id = ? and patient_id = ?", practitioner.id(), patient);
    }

    @Test
    void readingTheRecordLeavesWhoWhenAndByWhichAccess() throws Exception {
        mockMvc.perform(get("/api/clinical-records/" + patient).with(asArias)).andExpect(status().isOk());

        List<Map<String, Object>> readings = readingsBy(arias);
        assertThat(readings).hasSize(1);
        assertThat(readings.get(0).get("access")).isEqualTo("VISIT_TODAY");
        assertThat(readings.get(0).get("attachment_id")).isNull();
        assertThat(((Timestamp) readings.get(0).get("read_at")).toLocalDateTime())
                .isEqualTo(LocalDateTime.of(2026, 3, 10, 0, 0));
    }

    @Test
    void everyReadingIsOneMore() throws Exception {
        mockMvc.perform(get("/api/clinical-records/" + patient).with(asArias)).andExpect(status().isOk());
        mockMvc.perform(get("/api/clinical-records/" + patient).with(asArias)).andExpect(status().isOk());

        assertThat(readingsBy(arias)).hasSize(2);
    }

    @Test
    void aRefusedReadingLeavesNothing() throws Exception {
        mockMvc.perform(get("/api/clinical-records/" + patient).with(asAnalyst)).andExpect(status().isForbidden());

        assertThat(readingsBy(analyst)).isEmpty();
    }

    @Test
    void readingByAGrantSaysSo() throws Exception {
        String check = pendingCheck();
        send(asAnalyst, "/api/checks/" + check + "/access-grants", """
                {"reason": "Informar el análisis"}
                """).andExpect(status().isCreated());

        mockMvc.perform(get("/api/clinical-records/" + patient).with(asAnalyst)).andExpect(status().isOk());

        assertThat(readingsBy(analyst)).extracting(reading -> reading.get("access")).containsExactly("ACCESS_GRANT");
    }

    @Test
    void readingAPatientSeenBeforeSaysSo() throws Exception {
        send(asArias, "/api/consultations", """
                {"patientId": "%s", "notes": "Control."}
                """.formatted(patient)).andExpect(status().isCreated());
        // Cancelled by hand, the appointment leaves no visit today: only having seen the patient remains.
        mockMvc.perform(post("/api/appointments/" + appointment + "/cancel")).andExpect(status().isOk());

        mockMvc.perform(get("/api/clinical-records/" + patient).with(asArias)).andExpect(status().isOk());

        assertThat(readingsBy(arias)).extracting(reading -> reading.get("access"))
                .containsExactly("SEEN_WITHIN_PERIOD");
    }

    @Test
    void downloadingAnAttachmentIsAReadingOfThatFile() throws Exception {
        String check = pendingCheck();
        String attachment = idOf(mockMvc.perform(multipart("/api/checks/" + check + "/attachments")
                        .file(new MockMultipartFile("file", "informe.pdf", "application/pdf", PDF))
                        .with(asArias))
                .andExpect(status().isCreated())
                .andReturn());

        mockMvc.perform(get("/api/attachments/" + attachment).with(asArias)).andExpect(status().isOk());

        List<Map<String, Object>> readings = readingsBy(arias);
        assertThat(readings).hasSize(1);
        assertThat(readings.get(0).get("access")).isEqualTo("VISIT_TODAY");
        assertThat(readings.get(0).get("attachment_id")).isEqualTo(UUID.fromString(attachment));
    }

    @Test
    void aRefusedDownloadLeavesNothing() throws Exception {
        String check = pendingCheck();
        String attachment = idOf(mockMvc.perform(multipart("/api/checks/" + check + "/attachments")
                        .file(new MockMultipartFile("file", "informe.pdf", "application/pdf", PDF))
                        .with(asArias))
                .andExpect(status().isCreated())
                .andReturn());

        mockMvc.perform(get("/api/attachments/" + attachment).with(asAnalyst)).andExpect(status().isForbidden());

        assertThat(readingsBy(analyst)).isEmpty();
    }

    @Test
    void theListsThatShowNoClinicalContentAreNotReadings() throws Exception {
        pendingCheck();
        send(asArias, "/api/consultations", """
                {"patientId": "%s", "notes": "Control.", "orders": [{"description": "Radiografía de tórax"}]}
                """.formatted(patient)).andExpect(status().isCreated());

        mockMvc.perform(get("/api/checks/pending").with(asAnalyst)).andExpect(status().isOk());
        mockMvc.perform(get("/api/orders/mine").with(asArias)).andExpect(status().isOk());

        assertThat(readingsBy(analyst)).isEmpty();
        assertThat(readingsBy(arias)).isEmpty();
    }
}
