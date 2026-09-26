package com.clinic.schedule.clinicalrecord.adapter.in.http;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
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
import com.clinic.schedule.arrival.ArrivalFixture;
import com.clinic.schedule.clinicalrecord.PractitionerUserFixture;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Files attached to consultations and checks over the real stack (D-57): uploaded by whoever may
 * read the record, told apart by their content, listed in the record, downloaded only by whoever
 * may read it, and enough to make a check's result. "Today" is 10 March 2026.
 */
@SpringBootTest
@AutoConfigureMockMvc
@WithMockUser(roles = "RECEPTION")
class AttachmentApiIT {

    private static final byte[] PDF = "%PDF-1.4\n% informe de laboratorio\n".getBytes(StandardCharsets.US_ASCII);
    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n', 0, 0, 0, 13};

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
    private RequestPostProcessor asStranger;
    private UUID patient;

    @BeforeEach
    void setUp() throws Exception {
        PractitionerFixture practitioners = new PractitionerFixture(mockMvc, json);
        PractitionerUserFixture users = new PractitionerUserFixture(jdbc, userDetails);
        arias = practitioners.workingEveryDay();
        asArias = users.userFor(arias.id());
        asStranger = users.userFor(practitioners.workingEveryDay().id());
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

    private String idOf(MvcResult result) throws Exception {
        return json.readTree(result.getResponse().getContentAsString()).get("id").asText();
    }

    private ResultActions upload(RequestPostProcessor as, String path, String name, byte[] content) throws Exception {
        return mockMvc.perform(multipart(path).file(new MockMultipartFile("file", name, "application/octet-stream", content))
                .with(as));
    }

    private String consultation() throws Exception {
        return idOf(mockMvc.perform(post("/api/consultations").with(asArias)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"patientId": "%s", "notes": "Control."}
                                """.formatted(patient)))
                .andExpect(status().isCreated())
                .andReturn());
    }

    private String pendingCheck() throws Exception {
        return idOf(mockMvc.perform(post("/api/checks").with(asArias)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"patientId": "%s", "description": "Extracción de sangre"}
                                """.formatted(patient)))
                .andExpect(status().isCreated())
                .andReturn());
    }

    @Test
    void aFileIsAttachedListedAndDownloadedAsItWasUploaded() throws Exception {
        String consultation = consultation();

        String attachment = idOf(upload(asArias, "/api/consultations/" + consultation + "/attachments",
                "C:\\fotos\\lesión.png", PNG)
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.consultationId").value(consultation))
                .andExpect(jsonPath("$.fileName").value("lesión.png"))
                .andExpect(jsonPath("$.mediaType").value("image/png"))
                .andExpect(jsonPath("$.sizeBytes").value(PNG.length))
                .andReturn());

        mockMvc.perform(get("/api/clinical-records/" + patient).with(asArias))
                .andExpect(jsonPath("$.attachments", hasSize(1)))
                .andExpect(jsonPath("$.attachments[0].id").value(attachment));

        mockMvc.perform(get("/api/attachments/" + attachment).with(asArias))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "image/png"))
                .andExpect(header().string("Content-Disposition", containsString("attachment")))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(content().bytes(PNG));
    }

    @Test
    void whoMayNotReadTheRecordNeitherAttachesNorDownloads() throws Exception {
        String consultation = consultation();
        String attachment = idOf(upload(asArias, "/api/consultations/" + consultation + "/attachments", "informe.pdf", PDF)
                .andExpect(status().isCreated())
                .andReturn());

        upload(asStranger, "/api/consultations/" + consultation + "/attachments", "informe.pdf", PDF)
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/attachments/" + attachment).with(asStranger)).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/attachments/" + UUID.randomUUID()).with(asArias)).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/attachments/" + attachment)).andExpect(status().isForbidden());
        upload(asArias, "/api/checks/" + UUID.randomUUID() + "/attachments", "informe.pdf", PDF)
                .andExpect(status().isForbidden());
    }

    @Test
    void onlyPdfPngAndJpegUpToTenMegabytesAreKept() throws Exception {
        String consultation = consultation();

        upload(asArias, "/api/consultations/" + consultation + "/attachments", "informe.pdf",
                "no soy un PDF".getBytes(StandardCharsets.UTF_8))
                .andExpect(status().isUnsupportedMediaType());
        upload(asArias, "/api/consultations/" + consultation + "/attachments", "enorme.pdf",
                Arrays.copyOf(PDF, 10 * 1024 * 1024 + 1))
                .andExpect(status().isPayloadTooLarge());
        mockMvc.perform(multipart("/api/consultations/" + consultation + "/attachments").with(asArias))
                .andExpect(status().isBadRequest());

        mockMvc.perform(get("/api/clinical-records/" + patient).with(asArias))
                .andExpect(jsonPath("$.attachments", hasSize(0)));
    }

    @Test
    void anAttachedFileIsEnoughForACheckResult() throws Exception {
        String check = pendingCheck();

        mockMvc.perform(post("/api/checks/" + check + "/result").with(asArias)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());

        upload(asArias, "/api/checks/" + check + "/attachments", "hemograma.pdf", PDF)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.checkId").value(check));
        mockMvc.perform(post("/api/checks/" + check + "/result").with(asArias)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pending").value(false))
                .andExpect(jsonPath("$.result.text").doesNotExist());
    }
}
