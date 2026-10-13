package com.clinic.schedule.clinicalrecord.adapter.in.screen;

import static com.clinic.schedule.portal.PatientAccountFixture.PASSWORD;
import static com.clinic.schedule.portal.PatientAccountFixture.RECEPTION;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.test.context.TestSecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import com.clinic.schedule.appointment.PractitionerFixture;
import com.clinic.schedule.appointment.PractitionerFixture.CreatedPractitioner;
import com.clinic.schedule.clinicalrecord.PractitionerUserFixture;
import com.clinic.schedule.portal.KeptAccountMail;
import com.clinic.schedule.portal.PatientAccountFixture;
import com.clinic.schedule.portal.PatientAccountFixture.Patient;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * The screens of the copies of records (D-95): the patient asks for one on "Mi historia clínica" and downloads it;
 * the practitioners review and authenticate it from "Copias pedidas", or ask for access first; reception hands the
 * envelopes over from "Copias para retirar". "Today" is 10 March 2026, at midnight.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(KeptAccountMail.class)
class RecordCopyScreenIT {

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
        TestSecurityContextHolder.setAuthentication(UsernamePasswordAuthenticationToken.authenticated("recepcion",
                null, List.of(new SimpleGrantedAuthority("ROLE_RECEPTION"))));
        CreatedPractitioner stranger;
        try {
            PractitionerFixture practitioners = new PractitionerFixture(mockMvc, json);
            arias = practitioners.workingEveryDay();
            stranger = practitioners.workingEveryDay();
        } finally {
            TestSecurityContextHolder.clearContext();
        }
        PractitionerUserFixture users = new PractitionerUserFixture(jdbc, userDetails);
        asArias = users.userFor(arias.id());
        asStranger = users.userFor(stranger.id());
    }

    private MockHttpSession logIn(Patient patient) throws Exception {
        return (MockHttpSession) mockMvc.perform(post("/portal/login").with(csrf())
                        .param("documentType", "DNI").param("documentNumber", patient.dni())
                        .param("password", PASSWORD))
                .andExpect(redirectedUrl("/portal/appointments"))
                .andReturn().getRequest().getSession(false);
    }

    /** Arias sees the patient today, so may read their record. */
    private void bookedTodayWithArias(Patient patient) throws Exception {
        mockMvc.perform(post("/api/appointments").with(RECEPTION).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"patientId": "%s", "practitionerId": "%s", "reasonId": "%s", "startsAt": "2026-03-10T10:00:00"}
                                """.formatted(patient.id(), arias.id(), arias.consulta())))
                .andExpect(status().isCreated());
    }

    private String ask(MockHttpSession session, String delivery) throws Exception {
        mockMvc.perform(post("/portal/record-copies").session(session).with(csrf()).param("delivery", delivery))
                .andExpect(redirectedUrl("/portal/record-copies"))
                .andExpect(flash().attribute("message", containsString("dentro de las 48 horas")));
        return jdbc.queryForObject("select cast(id as varchar) from record_copy order by requested_at desc, id limit 1",
                String.class);
    }

    private String copyOf(Patient patient) {
        return jdbc.queryForObject("select cast(id as varchar) from record_copy where patient_id = cast(? as uuid)",
                String.class, patient.id());
    }

    @Test
    void aPatientAsksForACopyOnTheWebAndDownloadsItOnceAuthenticated() throws Exception {
        Patient patient = accounts.withAccount();
        bookedTodayWithArias(patient);
        MockHttpSession session = logIn(patient);

        mockMvc.perform(get("/portal/record-copies").session(session))
                .andExpect(content().string(containsString("Mi historia clínica")))
                .andExpect(content().string(containsString("Todavía no pediste ninguna copia.")));
        mockMvc.perform(post("/portal/record-copies").session(session).with(csrf()))
                .andExpect(flash().attribute("error", "Elegí cómo querés recibir la copia."));
        ask(session, "PDF");
        String copy = copyOf(patient);
        mockMvc.perform(post("/portal/record-copies").session(session).with(csrf()).param("delivery", "PDF"))
                .andExpect(flash().attribute("error", containsString("Ya hay una copia pedida")));
        mockMvc.perform(get("/portal/record-copies").session(session))
                .andExpect(content().string(containsString("Esperando que un profesional la autentique")))
                .andExpect(content().string(not(containsString("Descargar"))));

        mockMvc.perform(get("/record-copies").with(asArias))
                .andExpect(content().string(containsString("Ana Pérez")))
                .andExpect(content().string(containsString("Quedan 48 h")))
                .andExpect(content().string(containsString("Revisar")));
        mockMvc.perform(get("/record-copies/" + copy).with(asArias))
                .andExpect(content().string(containsString("Autenticar copia")))
                .andExpect(content().string(containsString("Subir a la historia")));
        mockMvc.perform(get("/record-copies/" + copy + "/pdf").with(asArias))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", containsString("inline")));
        mockMvc.perform(post("/record-copies/" + copy + "/authentication").with(asArias).with(csrf())
                        .param("assembly", "1"))
                .andExpect(flash().attribute("error", "Marcá que revisaste la copia antes de autenticarla."));
        mockMvc.perform(post("/record-copies/" + copy + "/authentication").with(asArias).with(csrf())
                        .param("assembly", "1").param("reviewed", "true"))
                .andExpect(redirectedUrl("/record-copies/" + copy))
                .andExpect(flash().attribute("message", containsString("ya puede descargarla")));
        mockMvc.perform(get("/record-copies/" + copy).with(asArias))
                .andExpect(content().string(not(containsString("Autenticar copia"))))
                .andExpect(content().string(containsString("Dra. Arias")));

        mockMvc.perform(get("/portal/record-copies").session(session))
                .andExpect(content().string(containsString("Lista para descargar")))
                .andExpect(content().string(containsString("/portal/record-copies/" + copy + "/pdf")));
        mockMvc.perform(get("/portal/record-copies/" + copy + "/pdf").session(session))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_PDF));
    }

    @Test
    void aPractitionerFindsACopyByItsCodeAndPutsItTogetherAgainAfterUploadingWhatWasMissing() throws Exception {
        Patient patient = accounts.withAccount();
        bookedTodayWithArias(patient);
        ask(logIn(patient), "PDF");
        String copy = copyOf(patient);
        String code = jdbc.queryForObject("select code from record_copy where id = cast(? as uuid)", String.class, copy);

        mockMvc.perform(get("/record-copies/verify").param("code", code.toLowerCase()).with(asArias))
                .andExpect(redirectedUrl("/record-copies/" + copy));
        mockMvc.perform(get("/record-copies/verify").param("code", "0000-0000-0000").with(asArias))
                .andExpect(redirectedUrl("/record-copies"))
                .andExpect(flash().attribute("error", "No hay ninguna copia con ese código."));

        mockMvc.perform(multipart("/record-copies/" + copy + "/attachments")
                        .file(new MockMultipartFile("file", "papel.pdf", "application/pdf",
                                "%PDF-1.4\n% escaneada\n".getBytes(StandardCharsets.US_ASCII)))
                        .with(asArias).with(csrf()))
                .andExpect(redirectedUrl("/record-copies/" + copy))
                .andExpect(flash().attribute("message", containsString("Volvé a armar la copia")));
        mockMvc.perform(post("/record-copies/" + copy + "/reassembly").with(asArias).with(csrf()))
                .andExpect(flash().attribute("message", containsString("Copia armada de nuevo")));
        mockMvc.perform(post("/record-copies/" + copy + "/authentication").with(asArias).with(csrf())
                        .param("assembly", "1").param("reviewed", "true"))
                .andExpect(flash().attribute("error", containsString("se armó de nuevo")));
        mockMvc.perform(get("/clinical-records/" + patient.id()).with(asArias))
                .andExpect(content().string(containsString("papel.pdf")));
    }

    @Test
    void aPractitionerWhoMayNotReadTheRecordAsksForAccessFromTheCopy() throws Exception {
        Patient patient = accounts.withAccount();
        ask(logIn(patient), "PDF");
        String copy = copyOf(patient);

        mockMvc.perform(get("/record-copies").with(asStranger))
                .andExpect(content().string(containsString("Ver pedido")));
        mockMvc.perform(get("/record-copies/" + copy).with(asStranger))
                .andExpect(content().string(containsString("No tenés acceso a esta historia")))
                .andExpect(content().string(not(containsString("Autenticar copia"))));
        mockMvc.perform(get("/record-copies/" + copy + "/pdf").with(asStranger)).andExpect(status().isForbidden());

        mockMvc.perform(post("/record-copies/" + copy + "/access-grants").with(asStranger).with(csrf())
                        .param("reason", " "))
                .andExpect(flash().attribute("error", "Escribí el motivo del pedido de acceso."));
        mockMvc.perform(post("/record-copies/" + copy + "/access-grants").with(asStranger).with(csrf())
                        .param("reason", "Autenticar la copia"))
                .andExpect(flash().attribute("message", containsString("Acceso autorizado")));
        mockMvc.perform(get("/record-copies/" + copy).with(asStranger))
                .andExpect(content().string(containsString("Autenticar copia")));
        mockMvc.perform(get("/clinical-records/" + patient.id()).with(asStranger))
                .andExpect(content().string(containsString("Autenticar una copia de la historia")));
    }

    @Test
    void receptionHandsTheEnvelopeOverAndReadsNothingOfIt() throws Exception {
        Patient patient = accounts.withAccount();
        bookedTodayWithArias(patient);
        MockHttpSession session = logIn(patient);
        ask(session, "PAPER");
        String copy = copyOf(patient);
        mockMvc.perform(post("/record-copies/" + copy + "/authentication").with(asArias).with(csrf())
                        .param("assembly", "1").param("reviewed", "true"))
                .andExpect(flash().attribute("message", containsString("dejala en recepción en un sobre cerrado")));

        mockMvc.perform(get("/record-copy-envelopes").with(RECEPTION))
                .andExpect(content().string(containsString("Copias para retirar")))
                .andExpect(content().string(containsString("Ana Pérez")))
                .andExpect(content().string(not(containsString("Dra. Arias"))));
        mockMvc.perform(post("/record-copy-envelopes/" + copy + "/handover").with(RECEPTION).with(csrf()))
                .andExpect(flash().attribute("error", "Marcá que viste el documento de quien la retira."));
        mockMvc.perform(post("/record-copy-envelopes/" + copy + "/handover").with(RECEPTION).with(csrf())
                        .param("documentSeen", "true"))
                .andExpect(redirectedUrl("/record-copy-envelopes"))
                .andExpect(flash().attribute("message", "Copia entregada."));
        mockMvc.perform(get("/portal/record-copies").session(session))
                .andExpect(content().string(containsString("Entregada el")));

        mockMvc.perform(get("/record-copies").with(RECEPTION)).andExpect(status().isForbidden());
        mockMvc.perform(get("/record-copies/" + copy + "/pdf").with(RECEPTION)).andExpect(status().isForbidden());
        mockMvc.perform(get("/record-copy-envelopes").with(asArias)).andExpect(status().isForbidden());
    }

    @Test
    void theHeadersLeadToTheCopies() throws Exception {
        Patient patient = accounts.withAccount();

        mockMvc.perform(get("/portal/appointments").session(logIn(patient)))
                .andExpect(content().string(containsString("Mi historia clínica")));
        mockMvc.perform(get("/record-copies").with(asArias))
                .andExpect(content().string(containsString("href=\"/record-copies\"")));
        mockMvc.perform(get("/record-copy-envelopes").with(RECEPTION))
                .andExpect(content().string(containsString("href=\"/record-copy-envelopes\"")));
    }
}
