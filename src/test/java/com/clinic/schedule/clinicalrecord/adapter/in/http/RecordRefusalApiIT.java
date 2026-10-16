package com.clinic.schedule.clinicalrecord.adapter.in.http;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
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
 * Every refused attempt to reach clinical content is recorded, and the admins read them (D-72): a
 * practitioner refused a record or a file, and anyone refused a clinical route by their role. The 403
 * stays as it was. "Today" is 10 March 2026, at midnight.
 */
@SpringBootTest
@AutoConfigureMockMvc
@WithMockUser(roles = "RECEPTION")
class RecordRefusalApiIT {

    private static final byte[] PDF = "%PDF-1.4\n% informe\n".getBytes(StandardCharsets.US_ASCII);

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
    private String analystUser;
    private UUID analyst;
    private String dni;
    private UUID patient;

    @BeforeEach
    void setUp() throws Exception {
        PractitionerFixture practitioners = new PractitionerFixture(mockMvc, json);
        PractitionerUserFixture users = new PractitionerUserFixture(jdbc, userDetails);
        CreatedPractitioner arias = practitioners.workingEveryDay();
        analyst = practitioners.workingEveryDay().id();
        asArias = users.userFor(arias.id());
        asAnalyst = users.userFor(analyst);
        analystUser = jdbc.queryForObject("select username from app_user where practitioner_id = ?", String.class, analyst);
        dni = String.valueOf(ThreadLocalRandom.current().nextInt(10_000_000, 99_999_999));
        patient = UUID.fromString(idOf(mockMvc.perform(post("/api/patients").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fullName": "Intento %s", "documentType": "DNI", "documentNumber": "%s"}
                                """.formatted(UUID.randomUUID().toString().substring(0, 8), dni)))
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
    void aPractitionerRefusedARecordLeavesWhoWhoseAndWhen() throws Exception {
        mockMvc.perform(get("/api/clinical-records/" + patient).with(asAnalyst))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403));

        List<Map<String, Object>> refusals = refusalsBy(analystUser);
        assertThat(refusals).hasSize(1);
        assertThat(refusals.get(0).get("attempt")).isEqualTo("RECORD");
        assertThat(refusals.get(0).get("practitioner_id")).isEqualTo(analyst);
        assertThat(refusals.get(0).get("patient_id")).isEqualTo(patient);
        assertThat(jdbc.queryForObject("select count(*) from record_reading where practitioner_id = ?", Integer.class,
                analyst)).isZero();
    }

    @Test
    void aRefusedFileIsRecordedWithWhoseItIsOrWithoutWhenItDoesNotExist() throws Exception {
        String check = idOf(mockMvc.perform(post("/api/checks").with(asArias).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"patientId": "%s", "description": "Extracción de sangre"}
                                """.formatted(patient)))
                .andExpect(status().isCreated())
                .andReturn());
        String attachment = idOf(mockMvc.perform(multipart("/api/checks/" + check + "/attachments")
                        .file(new MockMultipartFile("file", "informe.pdf", "application/pdf", PDF))
                        .with(asArias))
                .andExpect(status().isCreated())
                .andReturn());

        mockMvc.perform(get("/api/attachments/" + attachment).with(asAnalyst)).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/attachments/" + UUID.randomUUID()).with(asAnalyst)).andExpect(status().isForbidden());

        List<Map<String, Object>> refusals = refusalsBy(analystUser);
        assertThat(refusals).extracting(refusal -> refusal.get("attempt")).containsOnly("ATTACHMENT");
        assertThat(refusals).extracting(refusal -> refusal.get("patient_id")).containsExactlyInAnyOrder(patient, null);
        assertThat(refusals).extracting(refusal -> refusal.get("attachment_id"))
                .contains(UUID.fromString(attachment));
    }

    @Test
    void aReadingThatIsAllowedIsNoRefusal() throws Exception {
        mockMvc.perform(get("/api/clinical-records/" + patient).with(asArias)).andExpect(status().isOk());

        assertThat(jdbc.queryForObject("select count(*) from record_refusal where patient_id = ?", Integer.class, patient))
                .isZero();
    }

    @Test
    void aClinicalRouteRefusedToARoleIsRecordedWithThePatientItNames() throws Exception {
        RequestPostProcessor reception = user(userDetails.loadUserByUsername("recepcion"));
        mockMvc.perform(get("/api/clinical-records/" + patient).with(reception))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403));

        List<Map<String, Object>> refusals = jdbc.queryForList(
                "select attempt, route, practitioner_id from record_refusal where refused_by = 'recepcion' and patient_id = ?",
                patient);
        assertThat(refusals).extracting(refusal -> refusal.get("route")).containsExactly(
                "GET /api/clinical-records/" + patient);
        assertThat(refusals).extracting(refusal -> refusal.get("attempt")).containsOnly("ROUTE");
        assertThat(refusals).extracting(refusal -> refusal.get("practitioner_id")).containsOnly((Object) null);
    }

    @Test
    void theAdminRefusedAConsultationIsRecordedWithNoPatient() throws Exception {
        String before = "GET /api/consultations/" + UUID.randomUUID();
        mockMvc.perform(get(before.substring(4)).with(user(userDetails.loadUserByUsername("admin"))))
                .andExpect(status().isForbidden());

        assertThat(jdbc.queryForList("select patient_id from record_refusal where refused_by = 'admin' and route = ?",
                before)).hasSize(1).first().extracting(row -> row.get("patient_id")).isNull();
    }

    /** Only the clinical routes, only who is logged in, and not a browser's write refused for its missing token. */
    @Test
    void whatIsNotAClinicalRouteRefusedToSomeoneIsNotRecorded() throws Exception {
        String queue = "/api/my-queue";
        mockMvc.perform(get(queue).with(user(userDetails.loadUserByUsername("recepcion")))).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/clinical-records/" + patient).with(anonymous())).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/consultations").with(asAnalyst).with(request -> {
                    request.setRequestedSessionId("a-browser-s-session"); // a session's cookie, which asks for the token
                    return request;
                }).contentType(MediaType.APPLICATION_JSON).content("{\"patientId\": \"" + patient + "\"}"))
                .andExpect(status().isForbidden());

        assertThat(jdbc.queryForObject("select count(*) from record_refusal where route like ?", Integer.class,
                "%/my-queue")).isZero();
        assertThat(jdbc.queryForObject("select count(*) from record_refusal where patient_id = ?", Integer.class, patient))
                .isZero();
        assertThat(refusalsBy(analystUser)).isEmpty();
    }

    @Test
    void theAdminsReadThemByPatientAndUser() throws Exception {
        mockMvc.perform(get("/api/clinical-records/" + patient).with(asAnalyst)).andExpect(status().isForbidden());
        RequestPostProcessor admin = user(userDetails.loadUserByUsername("admin"));

        mockMvc.perform(get("/api/record-refusals").with(admin).param("patient", dni))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.refusals", hasSize(1)))
                .andExpect(jsonPath("$.refusals[0].refusedBy").value(analystUser))
                .andExpect(jsonPath("$.refusals[0].practitioner.id").value(analyst.toString()))
                .andExpect(jsonPath("$.refusals[0].patient.document").value("DNI " + dni))
                .andExpect(jsonPath("$.refusals[0].attempt").value("RECORD"))
                .andExpect(jsonPath("$.refusals[0].refusedAt").value("2026-03-10T00:00:00"));
        mockMvc.perform(get("/api/record-refusals").with(admin).param("user", analystUser))
                .andExpect(jsonPath("$.refusals", hasSize(1)));
        mockMvc.perform(get("/api/record-refusals").with(admin).param("user", "nadie"))
                .andExpect(jsonPath("$.refusals", hasSize(0)));
        mockMvc.perform(get("/api/record-refusals").with(admin).param("from", "2026-03-10").param("to", "2026-03-01"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void onlyTheAdminsReadThem() throws Exception {
        mockMvc.perform(get("/api/record-refusals").with(asArias)).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/record-refusals")).andExpect(status().isForbidden());
    }

    private List<Map<String, Object>> refusalsBy(String username) {
        return jdbc.queryForList("select attempt, practitioner_id, patient_id, attachment_id, route from record_refusal "
                + "where refused_by = ?", username);
    }

    private static RequestPostProcessor anonymous() {
        return org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.anonymous();
    }

    private String idOf(MvcResult result) throws Exception {
        return json.readTree(result.getResponse().getContentAsString()).get("id").asText();
    }
}
