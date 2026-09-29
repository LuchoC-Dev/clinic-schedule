package com.clinic.schedule.clinicalrecord.adapter.in.http;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

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
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import com.clinic.schedule.appointment.PractitionerFixture;
import com.clinic.schedule.appointment.PractitionerFixture.CreatedPractitioner;
import com.clinic.schedule.arrival.ArrivalFixture;
import com.clinic.schedule.clinicalrecord.PractitionerUserFixture;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * The admins read who read which clinical record (D-71): who — the practitioner and their user —,
 * whose, when, by which access, and whether it was a file; never what was read, nor the file's name.
 * "Today" is 10 March 2026, at midnight, so the readings made here fall on that day.
 */
@SpringBootTest
@AutoConfigureMockMvc
@WithMockUser(roles = "RECEPTION")
class ReadingLogApiIT {

    private static final byte[] PDF = "%PDF-1.4\n% informe\n".getBytes(StandardCharsets.US_ASCII);
    private static final String NOTES = "Dolor lumbar desde hace una semana.";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper json;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private UserDetailsService userDetails;

    private RequestPostProcessor admin;
    private CreatedPractitioner arias;
    private RequestPostProcessor asArias;
    private String ariasUser;
    private String dni;
    private String patientName;
    private UUID patient;

    @BeforeEach
    void setUp() throws Exception {
        admin = user(userDetails.loadUserByUsername("admin"));
        arias = new PractitionerFixture(mockMvc, json).workingEveryDay();
        asArias = new PractitionerUserFixture(jdbc, userDetails).userFor(arias.id());
        ariasUser = jdbc.queryForObject("select username from app_user where practitioner_id = ?", String.class, arias.id());
        dni = String.valueOf(ThreadLocalRandom.current().nextInt(10_000_000, 99_999_999));
        patientName = "Lectora " + UUID.randomUUID().toString().substring(0, 8);
        patient = UUID.fromString(idOf(mockMvc.perform(post("/api/patients").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fullName": "%s", "documentType": "DNI", "documentNumber": "%s"}
                                """.formatted(patientName, dni)))
                .andExpect(status().isCreated())
                .andReturn()));
        String appointment = idOf(mockMvc.perform(post("/api/appointments").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"patientId": "%s", "practitionerId": "%s", "reasonId": "%s", "startsAt": "2026-03-10T10:00:00"}
                                """.formatted(patient, arias.id(), arias.consulta())))
                .andExpect(status().isCreated())
                .andReturn());
        new ArrivalFixture(mockMvc, jdbc).arrivedAndCalled(appointment, asArias);
    }

    @Test
    void aReadingShowsWhoWhoseWhenAndHowButNothingThatWasRead() throws Exception {
        mockMvc.perform(post("/api/consultations").with(asArias).contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"patientId": "%s", "notes": "%s"}
                        """.formatted(patient, NOTES))).andExpect(status().isCreated());
        mockMvc.perform(get("/api/clinical-records/" + patient).with(asArias)).andExpect(status().isOk());

        mockMvc.perform(get("/api/record-readings").with(admin).param("patient", dni))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.readings", hasSize(1)))
                .andExpect(jsonPath("$.readings[0].readAt").value("2026-03-10T00:00:00"))
                .andExpect(jsonPath("$.readings[0].practitioner.id").value(arias.id().toString()))
                .andExpect(jsonPath("$.readings[0].practitioner.fullName").value("Dra. Arias"))
                .andExpect(jsonPath("$.readings[0].readBy").value(ariasUser))
                .andExpect(jsonPath("$.readings[0].patient.fullName").value(patientName))
                .andExpect(jsonPath("$.readings[0].patient.document").value("DNI " + dni))
                .andExpect(jsonPath("$.readings[0].access").value("VISIT_TODAY"))
                .andExpect(jsonPath("$.readings[0].attachment").value(false))
                .andExpect(content().string(not(containsString(NOTES))));
    }

    /** A file's name may say what it holds, so the admins learn only that a file was read (D-71). */
    @Test
    void aFileReadShowsThatItWasAFileAndNotWhichOne() throws Exception {
        String check = idOf(mockMvc.perform(post("/api/checks").with(asArias).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"patientId": "%s", "description": "Biopsia"}
                                """.formatted(patient)))
                .andExpect(status().isCreated())
                .andReturn());
        String attachment = idOf(mockMvc.perform(multipart("/api/checks/" + check + "/attachments")
                        .file(new MockMultipartFile("file", "biopsia_positiva.pdf", "application/pdf", PDF))
                        .with(asArias))
                .andExpect(status().isCreated())
                .andReturn());
        mockMvc.perform(get("/api/attachments/" + attachment).with(asArias)).andExpect(status().isOk());

        mockMvc.perform(get("/api/record-readings").with(admin).param("patient", patientName))
                .andExpect(jsonPath("$.readings", hasSize(1)))
                .andExpect(jsonPath("$.readings[0].attachment").value(true))
                .andExpect(content().string(not(containsString("biopsia"))))
                .andExpect(content().string(not(containsString("Biopsia"))))
                .andExpect(content().string(not(containsString(attachment))));
    }

    @Test
    void theSearchNarrowsByPatientPractitionerAndDays() throws Exception {
        mockMvc.perform(get("/api/clinical-records/" + patient).with(asArias)).andExpect(status().isOk());

        mockMvc.perform(get("/api/record-readings").with(admin).param("practitionerId", arias.id().toString()))
                .andExpect(jsonPath("$.readings", hasSize(1)));
        mockMvc.perform(get("/api/record-readings").with(admin).param("patient", "nadie-se-llama-asi"))
                .andExpect(jsonPath("$.readings", hasSize(0)));
        mockMvc.perform(get("/api/record-readings").with(admin).param("practitionerId", UUID.randomUUID().toString()))
                .andExpect(jsonPath("$.readings", hasSize(0)));
        mockMvc.perform(get("/api/record-readings").with(admin).param("patient", dni)
                        .param("from", "2026-03-11").param("to", "2026-03-20"))
                .andExpect(jsonPath("$.readings", hasSize(0)));
        mockMvc.perform(get("/api/record-readings").with(admin).param("patient", dni)
                        .param("from", "2026-03-10").param("to", "2026-03-10"))
                .andExpect(jsonPath("$.readings", hasSize(1)));
    }

    /** Without days, the last week up to today. */
    @Test
    void withoutDaysItIsTheLastWeek() throws Exception {
        UUID practitioner = UUID.randomUUID();
        reading(practitioner, "antes", LocalDateTime.of(2026, 3, 3, 23, 59));
        reading(practitioner, "dentro", LocalDateTime.of(2026, 3, 4, 0, 0));

        mockMvc.perform(get("/api/record-readings").with(admin).param("practitionerId", practitioner.toString()))
                .andExpect(jsonPath("$.from").value("2026-03-04"))
                .andExpect(jsonPath("$.to").value("2026-03-10"))
                .andExpect(jsonPath("$.readings", hasSize(1)))
                .andExpect(jsonPath("$.readings[0].readBy").value("dentro"));
    }

    @Test
    void aReadingFromBeforeUsersWereShowsNoUser() throws Exception {
        UUID practitioner = UUID.randomUUID();
        reading(practitioner, null, LocalDateTime.of(2026, 3, 9, 12, 0));

        mockMvc.perform(get("/api/record-readings").with(admin).param("practitionerId", practitioner.toString()))
                .andExpect(jsonPath("$.readings", hasSize(1)))
                .andExpect(jsonPath("$.readings[0].readBy").doesNotExist());
    }

    @Test
    void atMostFiveHundredComeBackAndTheAnswerSaysThereWereMore() throws Exception {
        UUID practitioner = UUID.randomUUID();
        List<Object[]> rows = new ArrayList<>();
        for (int i = 0; i < 501; i++) {
            rows.add(new Object[] {UUID.randomUUID(), practitioner, patient,
                    Timestamp.valueOf(LocalDateTime.of(2025, 6, 1, 8, 0).plusMinutes(i))});
        }
        jdbc.batchUpdate("insert into record_reading (id, practitioner_id, patient_id, read_at, access) "
                + "values (?, ?, ?, ?, 'VISIT_TODAY')", rows);

        mockMvc.perform(get("/api/record-readings").with(admin).param("practitionerId", practitioner.toString())
                        .param("from", "2025-06-01").param("to", "2025-06-01"))
                .andExpect(jsonPath("$.readings", hasSize(500)))
                .andExpect(jsonPath("$.limitReached").value(true))
                .andExpect(jsonPath("$.readings[0].readAt").value("2025-06-01T16:20:00"));
        mockMvc.perform(get("/api/record-readings").with(admin).param("practitionerId", practitioner.toString())
                        .param("from", "2025-06-01").param("to", "2025-06-01").param("patient", dni))
                .andExpect(jsonPath("$.limitReached").value(true));
    }

    @Test
    void theFirstDayCannotComeAfterTheLast() throws Exception {
        mockMvc.perform(get("/api/record-readings").with(admin).param("from", "2026-03-10").param("to", "2026-03-01"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void onlyTheAdminsReadIt() throws Exception {
        mockMvc.perform(get("/api/record-readings").with(asArias)).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/record-readings")).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/record-readings").with(user(userDetails.loadUserByUsername("sala"))))
                .andExpect(status().isForbidden());
    }

    private void reading(UUID practitioner, String readBy, LocalDateTime at) {
        jdbc.update("insert into record_reading (id, practitioner_id, read_by, patient_id, read_at, access) "
                + "values (?, ?, ?, ?, ?, 'SEEN_WITHIN_PERIOD')", UUID.randomUUID(), practitioner, readBy, patient,
                Timestamp.valueOf(at));
    }

    private String idOf(MvcResult result) throws Exception {
        return json.readTree(result.getResponse().getContentAsString()).get("id").asText();
    }
}
