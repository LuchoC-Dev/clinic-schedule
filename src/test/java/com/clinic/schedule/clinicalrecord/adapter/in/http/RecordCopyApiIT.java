package com.clinic.schedule.clinicalrecord.adapter.in.http;

import static com.clinic.schedule.portal.PatientAccountFixture.PASSWORD;
import static com.clinic.schedule.portal.PatientAccountFixture.RECEPTION;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.openpdf.text.Document;
import org.openpdf.text.Paragraph;
import org.openpdf.text.pdf.PdfReader;
import org.openpdf.text.pdf.PdfWriter;
import org.openpdf.text.pdf.parser.PdfTextExtractor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.test.context.TestSecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import com.clinic.schedule.appointment.PractitionerFixture;
import com.clinic.schedule.appointment.PractitionerFixture.CreatedPractitioner;
import com.clinic.schedule.arrival.ArrivalFixture;
import com.clinic.schedule.clinicalrecord.PractitionerUserFixture;
import com.clinic.schedule.portal.KeptAccountMail;
import com.clinic.schedule.portal.PatientAccountFixture;
import com.clinic.schedule.portal.PatientAccountFixture.Patient;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * A copy of the clinical record over the real stack (D-95, D-96): asked for from the account, put together at once,
 * reviewed and authenticated by a practitioner who may read the record within the 48 hours, and handed over as a PDF
 * or on paper at reception — every handing a reading of the record. "Today" is 10 March 2026, at midnight.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(KeptAccountMail.class)
class RecordCopyApiIT {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper json;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private UserDetailsService userDetails;

    private PatientAccountFixture accounts;
    private CreatedPractitioner arias;
    private RequestPostProcessor asArias;
    private RequestPostProcessor asStranger;

    @BeforeEach
    void setUp() throws Exception {
        accounts = new PatientAccountFixture(mockMvc, json);
        PractitionerFixture practitioners = new PractitionerFixture(mockMvc, json);
        CreatedPractitioner[] created = new CreatedPractitioner[2];
        asReception(() -> {
            created[0] = practitioners.workingEveryDay();
            created[1] = practitioners.workingEveryDay();
        });
        arias = created[0];
        PractitionerUserFixture users = new PractitionerUserFixture(jdbc, userDetails);
        asArias = users.userFor(arias.id());
        asStranger = users.userFor(created[1].id());
    }

    private interface Steps {
        void run() throws Exception;
    }

    /** The fixtures that act as reception without saying so, with reception logged in for them. */
    private static void asReception(Steps steps) throws Exception {
        TestSecurityContextHolder.setAuthentication(UsernamePasswordAuthenticationToken.authenticated("recepcion",
                null, List.of(new SimpleGrantedAuthority("ROLE_RECEPTION"))));
        try {
            steps.run();
        } finally {
            TestSecurityContextHolder.clearContext();
        }
    }

    private static RequestPostProcessor as(Patient patient) {
        return httpBasic("DNI " + patient.dni(), PASSWORD);
    }

    private ResultActions send(RequestPostProcessor who, String path, String body) throws Exception {
        return mockMvc.perform(post(path).with(who).contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private JsonNode read(ResultActions result) throws Exception {
        return json.readTree(result.andReturn().getResponse().getContentAsString());
    }

    /** Arias sees the patient today, and writes a consultation of them: there is a record to copy. */
    private void seenTodayByArias(Patient patient) throws Exception {
        String appointment = read(send(RECEPTION, "/api/appointments", """
                {"patientId": "%s", "practitionerId": "%s", "reasonId": "%s", "startsAt": "2026-03-10T10:00:00"}
                """.formatted(patient.id(), arias.id(), arias.consulta())).andExpect(status().isCreated()))
                .get("id").asText();
        asReception(() -> new ArrivalFixture(mockMvc, jdbc).arrivedAndCalled(appointment, asArias));
        send(asArias, "/api/consultations", """
                {"patientId": "%s", "notes": "Control de presion arterial."}
                """.formatted(patient.id())).andExpect(status().isCreated());
    }

    private String ask(Patient asker, String forWhom, String delivery) throws Exception {
        return read(send(as(asker), "/api/portal/record-copies", """
                {"patientId": %s, "delivery": "%s"}
                """.formatted(forWhom == null ? "null" : "\"" + forWhom + "\"", delivery))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.state").value("WAITING")))
                .get("id").asText();
    }

    private ResultActions authenticate(RequestPostProcessor who, String copy, int assembly, boolean reviewed)
            throws Exception {
        return send(who, "/api/record-copies/" + copy + "/authentication",
                "{\"assembly\": %d, \"reviewed\": %s}".formatted(assembly, reviewed));
    }

    /** Every page's text of a PDF. */
    private static String text(byte[] pdf) throws Exception {
        PdfReader reader = new PdfReader(pdf);
        PdfTextExtractor extractor = new PdfTextExtractor(reader);
        StringBuilder text = new StringBuilder();
        for (int page = 1; page <= reader.getNumberOfPages(); page++) {
            text.append(extractor.getTextFromPage(page)).append('\n');
        }
        return text.toString();
    }

    private byte[] bytes(ResultActions result) {
        return result.andReturn().getResponse().getContentAsByteArray();
    }

    private List<Map<String, Object>> readings(String copy) {
        return jdbc.queryForList("select practitioner_id, read_by, access from record_reading where record_copy_id = ? "
                + "order by read_at", UUID.fromString(copy));
    }

    @Test
    void aPatientAsksForTheirCopyAPractitionerAuthenticatesItAndThePatientDownloadsIt() throws Exception {
        Patient patient = accounts.withAccount();
        seenTodayByArias(patient);

        String copy = ask(patient, null, "PDF");
        send(as(patient), "/api/portal/record-copies", "{\"delivery\": \"PDF\"}").andExpect(status().isConflict());
        mockMvc.perform(get("/api/portal/record-copies").with(as(patient)))
                .andExpect(jsonPath("$.askableFor[0].id").value(patient.id()))
                .andExpect(jsonPath("$.copies", hasSize(1)))
                .andExpect(jsonPath("$.copies[0].deadline").value("2026-03-12T00:00:00"))
                .andExpect(jsonPath("$.copies[0].own").value(true));
        mockMvc.perform(get("/api/portal/record-copies/" + copy + "/pdf").with(as(patient)))
                .andExpect(status().isConflict());

        JsonNode waiting = read(mockMvc.perform(get("/api/record-copies").with(asArias)).andExpect(status().isOk()));
        JsonNode mine = null;
        for (JsonNode one : waiting) {
            if (one.get("id").asText().equals(copy)) {
                mine = one;
            }
        }
        assertThat(mine).isNotNull();
        assertThat(mine.get("minutesLeft").asLong()).isEqualTo(48 * 60);
        assertThat(mine.get("mayRead").asBoolean()).isTrue();
        assertThat(mine.get("assembly").asInt()).isEqualTo(1);
        String code = mine.get("code").asText();

        String draft = text(bytes(mockMvc.perform(get("/api/record-copies/" + copy + "/pdf").with(asArias))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_PDF))
                // Shown in the browser, to review or print, as on the screen (D-106).
                .andExpect(header().string("Content-Disposition", startsWith("inline")))));
        assertThat(draft).contains("Control de presion arterial.", "SIN AUTENTICAR", "Código de verificación: " + code,
                "código " + code + " · página 1 de 1");

        authenticate(asArias, copy, 1, false).andExpect(status().isConflict());
        authenticate(asArias, copy, 2, true).andExpect(status().isConflict());
        authenticate(asArias, copy, 1, true)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.authentication.practitionerId").value(arias.id().toString()));
        authenticate(asArias, copy, 1, true).andExpect(status().isConflict());
        mockMvc.perform(get("/api/record-copies").with(asArias))
                .andExpect(jsonPath("$[?(@.id == '" + copy + "')]").isEmpty());

        mockMvc.perform(get("/api/portal/record-copies").with(as(patient)))
                .andExpect(jsonPath("$.copies[0].state").value("READY_TO_DOWNLOAD"));
        String delivered = text(bytes(mockMvc.perform(get("/api/portal/record-copies/" + copy + "/pdf")
                        .with(as(patient)))
                .andExpect(status().isOk())));
        assertThat(delivered).contains("Control de presion arterial.", "Autenticada", code)
                .doesNotContain("SIN AUTENTICAR");

        mockMvc.perform(get("/api/record-copies").param("code", code.toLowerCase()).with(asArias))
                .andExpect(jsonPath("$.id").value(copy));
        mockMvc.perform(get("/api/record-copies").param("code", "0000-0000-0000").with(asArias))
                .andExpect(status().isNotFound());

        List<Map<String, Object>> readings = readings(copy);
        assertThat(readings).hasSize(2);
        assertThat(readings.get(0).get("practitioner_id")).isEqualTo(arias.id());
        assertThat(readings.get(0).get("access")).isEqualTo("VISIT_TODAY");
        assertThat(readings.get(1).get("practitioner_id")).isNull();
        assertThat(readings.get(1).get("access")).isEqualTo("COPY_REQUEST");
        assertThat((String) readings.get(1).get("read_by")).startsWith("portal:");
    }

    @Test
    void whatIsUploadedBeforeAuthenticatingIsInTheCopyOnceItIsPutTogetherAgain() throws Exception {
        Patient patient = accounts.withAccount();
        seenTodayByArias(patient);
        String copy = ask(patient, null, "PDF");
        // The test's clock stands still: the consultation and the request happened an hour earlier, so the file
        // uploaded now comes after the copy was put together.
        jdbc.update("update consultation set recorded_at = timestamp '2026-03-09 22:00:00' where patient_id = ?",
                UUID.fromString(patient.id()));
        jdbc.update("update record_copy set requested_at = timestamp '2026-03-09 23:00:00', "
                + "assembled_at = timestamp '2026-03-09 23:00:00' where id = ?", UUID.fromString(copy));

        mockMvc.perform(multipart("/api/clinical-records/" + patient.id() + "/attachments")
                        .file(new MockMultipartFile("file", "historia-en-papel.pdf", "application/pdf",
                                "%PDF-1.4\n% escaneada\n".getBytes(StandardCharsets.US_ASCII)))
                        .with(asArias))
                .andExpect(status().isCreated());
        assertThat(text(bytes(mockMvc.perform(get("/api/record-copies/" + copy + "/pdf").with(asArias)))))
                .doesNotContain("historia-en-papel.pdf");

        send(asArias, "/api/record-copies/" + copy + "/reassembly", "").andExpect(status().isOk())
                .andExpect(jsonPath("$.assembly").value(2));
        assertThat(text(bytes(mockMvc.perform(get("/api/record-copies/" + copy + "/pdf").with(asArias)))))
                .contains("Archivos de la historia", "historia-en-papel.pdf", "Anexo 1",
                        "no pudo reproducirse");

        authenticate(asArias, copy, 1, true).andExpect(status().isConflict());
        authenticate(asArias, copy, 2, true).andExpect(status().isOk());
        send(asArias, "/api/record-copies/" + copy + "/reassembly", "").andExpect(status().isConflict());
    }

    /** A PDF of one page that says {@code text}, as a scanner would give one. */
    private static byte[] onePagePdf(String text) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Document document = new Document();
        PdfWriter.getInstance(document, out);
        document.open();
        document.add(new Paragraph(text));
        document.close();
        return out.toByteArray();
    }

    private static byte[] png() throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(40, 20, BufferedImage.TYPE_INT_RGB),
                "png", out);
        return out.toByteArray();
    }

    @Test
    void theFilesAttachedAreAnnexedAfterTheEntriesEachOnItsOwnPages() throws Exception {
        Patient patient = accounts.withAccount();
        seenTodayByArias(patient);
        mockMvc.perform(multipart("/api/clinical-records/" + patient.id() + "/attachments")
                        .file(new MockMultipartFile("file", "papel.pdf", "application/pdf", onePagePdf("Historia 2015")))
                        .with(asArias))
                .andExpect(status().isCreated());
        mockMvc.perform(multipart("/api/clinical-records/" + patient.id() + "/attachments")
                        .file(new MockMultipartFile("file", "foto.png", "image/png", png()))
                        .with(asArias))
                .andExpect(status().isCreated());
        String copy = ask(patient, null, "PDF");

        byte[] pdf = bytes(mockMvc.perform(get("/api/record-copies/" + copy + "/pdf").with(asArias)));

        assertThat(new PdfReader(pdf).getNumberOfPages()).isEqualTo(3);
        assertThat(text(pdf)).contains("Anexo 1 — papel.pdf", "Anexo 2 — foto.png", "página 3 de 3")
                .doesNotContain("no pudo reproducirse");
    }

    @Test
    void aCopyOnPaperIsHandedOverAtReceptionToWhoeverShowsTheirDocument() throws Exception {
        Patient patient = accounts.withAccount();
        seenTodayByArias(patient);
        String copy = ask(patient, null, "PAPER");

        mockMvc.perform(get("/api/record-copy-envelopes").with(RECEPTION))
                .andExpect(jsonPath("$[?(@.id == '" + copy + "')]").isEmpty());
        authenticate(asArias, copy, 1, true).andExpect(status().isOk());
        mockMvc.perform(get("/api/portal/record-copies/" + copy + "/pdf").with(as(patient)))
                .andExpect(status().isConflict());
        mockMvc.perform(get("/api/record-copy-envelopes").with(RECEPTION))
                .andExpect(jsonPath("$[?(@.id == '" + copy + "')].patient.fullName").value("Ana Pérez"));

        send(RECEPTION, "/api/record-copy-envelopes/" + copy + "/handover", "{\"documentSeen\": false}")
                .andExpect(status().isConflict());
        send(RECEPTION, "/api/record-copy-envelopes/" + copy + "/handover", "{\"documentSeen\": true}")
                .andExpect(status().isOk());
        send(RECEPTION, "/api/record-copy-envelopes/" + copy + "/handover", "{\"documentSeen\": true}")
                .andExpect(status().isConflict());

        mockMvc.perform(get("/api/record-copy-envelopes").with(RECEPTION))
                .andExpect(jsonPath("$[?(@.id == '" + copy + "')]").isEmpty());
        mockMvc.perform(get("/api/portal/record-copies").with(as(patient)))
                .andExpect(jsonPath("$.copies[0].state").value("HANDED_OVER"));
        List<Map<String, Object>> readings = readings(copy);
        assertThat(readings).hasSize(1);
        assertThat(readings.get(0).get("read_by")).isEqualTo("recepcion");
        assertThat(readings.get(0).get("access")).isEqualTo("COPY_REQUEST");

        // The admins read that a copy was handed over, by whom, and no practitioner (D-71, D-96).
        RequestPostProcessor admin = user(userDetails.loadUserByUsername("admin"));
        mockMvc.perform(get("/api/record-readings").with(admin).param("patient", patient.dni()))
                .andExpect(jsonPath("$.readings[?(@.copy == true)].readBy").value("recepcion"))
                .andExpect(jsonPath("$.readings[?(@.copy == true)].access").value("COPY_REQUEST"))
                .andExpect(jsonPath("$.readings[?(@.copy == true)].practitioner").value(contains((Object) null)));
        mockMvc.perform(get("/record-readings").with(admin).param("patient", patient.dni()))
                .andExpect(content().string(containsString("Copia de la historia")))
                .andExpect(content().string(containsString("Copia pedida por el paciente")));
    }

    @Test
    void aParentAsksForTheirChildUnder13OnlyOnceReceptionProvedTheRelation() throws Exception {
        Patient mother = accounts.withAccount("Marta Díaz", "1985-04-02");
        Patient father = accounts.withAccount("Raúl Díaz", "1983-01-15");
        Patient child = accounts.patient("Sofía Díaz", true, "2018-09-01");
        Patient teen = accounts.patient("Tomás Díaz", true, "2011-06-20");
        String group = read(send(RECEPTION, "/api/family-groups", "{\"name\": \"Familia Díaz\"}")
                .andExpect(status().isCreated())).get("id").asText();
        for (Patient adult : List.of(mother, father, teen)) {
            send(RECEPTION, "/api/family-groups/" + group + "/members",
                    "{\"patientId\": \"" + adult.id() + "\", \"shares\": true}").andExpect(status().isOk());
        }
        send(RECEPTION, "/api/family-groups/" + group + "/children", """
                {"patientId": "%s", "requestedBy": "%s", "relation": "MOTHER", "proofSeen": true}
                """.formatted(child.id(), mother.id())).andExpect(status().isOk());
        String unprovedDni = PatientAccountFixture.dni();
        send(as(mother), "/api/portal/family/children", """
                {"fullName": "Luz Díaz", "documentType": "DNI", "documentNumber": "%s", "birthDate": "2020-02-02",
                 "relation": "MOTHER", "groupId": "%s"}
                """.formatted(unprovedDni, group)).andExpect(status().isAccepted());
        String unproved = read(mockMvc.perform(get("/api/patients").with(RECEPTION).param("query", unprovedDni)))
                .get(0).get("id").asText();

        mockMvc.perform(get("/api/portal/record-copies").with(as(mother)))
                .andExpect(jsonPath("$.askableFor", hasSize(2)))
                .andExpect(jsonPath("$.askableFor[1].id").value(child.id()));
        String childsCopy = ask(mother, child.id(), "PDF");
        // The patients' application says whose each is: the mother's own, or her child's (D-112).
        String own = ask(mother, null, "PAPER");
        mockMvc.perform(get("/api/portal/record-copies").with(as(mother)))
                .andExpect(jsonPath("$.copies[?(@.id == '%s')].own".formatted(childsCopy)).value(false))
                .andExpect(jsonPath("$.copies[?(@.id == '%s')].patient.fullName".formatted(childsCopy))
                        .value("Sofía Díaz"))
                .andExpect(jsonPath("$.copies[?(@.id == '%s')].own".formatted(own)).value(true));

        send(as(mother), "/api/portal/record-copies", "{\"patientId\": \"" + unproved + "\", \"delivery\": \"PDF\"}")
                .andExpect(status().isForbidden());
        send(as(mother), "/api/portal/record-copies", "{\"patientId\": \"" + teen.id() + "\", \"delivery\": \"PDF\"}")
                .andExpect(status().isForbidden());
        send(as(father), "/api/portal/record-copies", "{\"patientId\": \"" + child.id() + "\", \"delivery\": \"PDF\"}")
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/portal/record-copies").with(as(father)))
                .andExpect(jsonPath("$.copies", hasSize(0)));
        mockMvc.perform(get("/api/portal/record-copies/" + childsCopy + "/pdf").with(as(father)))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/portal/record-copies/" + UUID.randomUUID() + "/pdf").with(as(mother)))
                .andExpect(status().isForbidden());
    }

    @Test
    void aPractitionerWhoMayNotReadTheRecordAsksForAccessToAuthenticateTheCopy() throws Exception {
        Patient patient = accounts.withAccount();
        seenTodayByArias(patient);
        String copy = ask(patient, null, "PDF");

        mockMvc.perform(get("/api/record-copies/" + copy).with(asStranger))
                .andExpect(jsonPath("$.mayRead").value(false));
        mockMvc.perform(get("/api/record-copies").with(asStranger))
                .andExpect(jsonPath("$[?(@.id == '" + copy + "')].mayRead").value(false));
        mockMvc.perform(get("/api/record-copies/" + copy + "/pdf").with(asStranger)).andExpect(status().isForbidden());
        authenticate(asStranger, copy, 1, true).andExpect(status().isForbidden());
        send(asStranger, "/api/record-copies/" + copy + "/reassembly", "").andExpect(status().isForbidden());
        assertThat(jdbc.queryForObject("select count(*) from record_refusal where patient_id = ?", Integer.class,
                UUID.fromString(patient.id()))).isEqualTo(1);

        send(asStranger, "/api/record-copies/" + copy + "/access-grants", "{\"reason\": \"Autenticar la copia\"}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.recordCopyId").value(copy))
                .andExpect(jsonPath("$.checkId").doesNotExist());
        authenticate(asStranger, copy, 1, true).andExpect(status().isOk());
        send(asStranger, "/api/record-copies/" + copy + "/access-grants", "{\"reason\": \"Otra vez\"}")
                .andExpect(status().isConflict());
        mockMvc.perform(get("/api/clinical-records/" + patient.id()).with(asArias))
                .andExpect(jsonPath("$.accessGrants[0].recordCopyId").value(copy));
    }

    @Test
    void theCopiesAreThePractitionersAndTheEnvelopesReceptionsByRoute() throws Exception {
        Patient patient = accounts.withAccount();
        String copy = ask(patient, null, "PAPER");

        mockMvc.perform(get("/api/record-copies").with(RECEPTION)).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/record-copies/" + copy + "/pdf").with(RECEPTION)).andExpect(status().isForbidden());
        // A clinical route refused to reception is recorded, as any (D-72).
        assertThat(jdbc.queryForObject("select count(*) from record_refusal where attempt = 'ROUTE' and route = ?",
                Integer.class, "GET /api/record-copies/" + copy + "/pdf")).isEqualTo(1);
        mockMvc.perform(get("/api/record-copy-envelopes").with(asArias)).andExpect(status().isForbidden());
        send(asArias, "/api/record-copy-envelopes/" + copy + "/handover", "{\"documentSeen\": true}")
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/record-copies").with(as(patient))).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/record-copies/" + UUID.randomUUID()).with(asArias)).andExpect(status().isNotFound());
    }
}
