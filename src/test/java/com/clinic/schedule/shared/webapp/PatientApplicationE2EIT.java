package com.clinic.schedule.shared.webapp;

import static com.clinic.schedule.portal.PatientAccountFixture.PASSWORD;
import static com.clinic.schedule.portal.PatientAccountFixture.RECEPTION;
import static com.clinic.schedule.portal.PatientAccountFixture.address;
import static com.clinic.schedule.portal.PatientAccountFixture.lastTokenTo;
import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.context.TestSecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;

import com.clinic.schedule.appointment.PractitionerFixture;
import com.clinic.schedule.appointment.PractitionerFixture.CreatedPractitioner;
import com.clinic.schedule.portal.KeptAccountMail;
import com.clinic.schedule.portal.PatientAccountFixture;
import com.clinic.schedule.portal.PatientAccountFixture.Patient;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.impl.driver.Driver;
import com.microsoft.playwright.options.AriaRole;

/**
 * The patients' application from end to end, on a phone (D-97): an email's address — the link of an activation,
 * `/portal/activation?token=`, as emails sent before the application existed carry it too — is its own screen, where
 * the patient chooses their password and logs in with their document (D-110, D-114); and a patient logs in and books an appointment at one of
 * the times the agenda offers (D-82, D-111). The application as Maven built it, on the tests' database and clock —
 * "today" is Tuesday 10 March 2026 —, driven in Chromium.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@Import(KeptAccountMail.class)
class PatientApplicationE2EIT {

    private static Playwright playwright;
    private static Browser browser;

    @LocalServerPort
    private int port;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper json;

    private Page page;

    @BeforeAll
    static void launch() throws Exception {
        // Playwright would download every browser it drives; the test needs only Chromium.
        Driver driver = Driver.ensureDriverInstalled(Map.of(), false);
        ProcessBuilder install = driver.createProcessBuilder();
        install.command().addAll(List.of("install", "chromium"));
        if (install.inheritIO().start().waitFor() != 0) {
            throw new IllegalStateException("Chromium could not be installed for the end-to-end tests");
        }
        playwright = Playwright.create(new Playwright.CreateOptions()
                .setEnv(Map.of("PLAYWRIGHT_SKIP_BROWSER_DOWNLOAD", "1")));
        browser = playwright.chromium().launch(new BrowserType.LaunchOptions().setHeadless(true));
    }

    @AfterAll
    static void close() {
        if (playwright != null) {
            playwright.close();
        }
    }

    /** A phone's screen: the patients' application is made for one first (D-97). */
    @BeforeEach
    void open() {
        page = browser.newContext(new Browser.NewContextOptions().setViewportSize(390, 844).setIsMobile(true)
                .setHasTouch(true)).newPage();
    }

    @AfterEach
    void closePage() {
        page.context().close();
    }

    private String at(String path) {
        return "http://localhost:" + port + path;
    }

    @Test
    void theActivationsEmailOpensThePatientsApplicationWhereTheyChooseTheirPasswordAndLogIn() throws Exception {
        PatientAccountFixture accounts = new PatientAccountFixture(mockMvc, json);
        Patient patient = accounts.adult();
        String email = address();
        accounts.open(patient, email);
        String token = lastTokenTo(email);

        page.navigate(at("/portal/activation?token=" + token));

        assertThat(page).hasURL(at("/portal/activation?token=" + token));
        assertThat(page.getByRole(AriaRole.HEADING, new Page.GetByRoleOptions().setName("Activá tu cuenta"))).isVisible();
        page.getByLabel("Contraseña").fill("una-clave-del-telefono");
        page.getByLabel("Repetila").fill("una-clave-del-telefono");
        page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Activar")).click();

        assertThat(page).hasURL(Pattern.compile("/portal/login\\?activated$"));
        assertThat(page.getByText("Cuenta activada: ingresá con tu documento y tu contraseña.")).isVisible();
        page.getByLabel("Número").fill(patient.dni());
        page.getByLabel("Contraseña").fill("una-clave-del-telefono");
        page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Ingresar")).click();

        assertThat(page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Salir"))).isVisible();
        assertThat(page.getByRole(AriaRole.NAVIGATION, new Page.GetByRoleOptions().setName("Secciones"))).isVisible();

        // Used, the same link is no use any more.
        page.navigate(at("/portal/activation?token=" + token));
        assertThat(page.getByRole(AriaRole.ALERT)).containsText("Este link ya no sirve");
    }

    /** One who works every day, whose "consulta" reception opened to the web (D-82). */
    private CreatedPractitioner bookedOnTheWeb() throws Exception {
        // The practitioners' fixture sends its requests as whoever the test is: reception, for the while.
        TestSecurityContextHolder.setAuthentication(UsernamePasswordAuthenticationToken.authenticated("recepcion",
                null, List.of(new SimpleGrantedAuthority("ROLE_RECEPTION"))));
        CreatedPractitioner practitioner;
        try {
            practitioner = new PractitionerFixture(mockMvc, json).workingEveryDay();
        } finally {
            TestSecurityContextHolder.clearContext();
        }
        mockMvc.perform(put("/api/practitioners/" + practitioner.id() + "/reasons/" + practitioner.consulta())
                        .with(RECEPTION).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"consulta\", \"durationMinutes\": 30, \"webBookable\": true}"))
                .andExpect(status().isOk());
        return practitioner;
    }

    @Test
    void aPatientLogsInOnTheirPhoneAndBooksAnAppointment() throws Exception {
        CreatedPractitioner arias = bookedOnTheWeb();
        Patient patient = new PatientAccountFixture(mockMvc, json).withAccount();

        page.navigate(at("/portal/"));
        assertThat(page).hasURL(Pattern.compile("/portal/login$"));
        page.getByLabel("Número").fill(patient.dni());
        page.getByLabel("Contraseña").fill(PASSWORD);
        page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Ingresar")).click();

        assertThat(page).hasURL(Pattern.compile("/portal/appointments$"));
        assertThat(page.getByText("No tenés turnos próximos.")).isVisible();
        page.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName("Sacar turno")).click();

        assertThat(page).hasURL(Pattern.compile("/portal/booking$"));
        page.getByLabel("Profesional y motivo").selectOption(arias.consulta().toString());
        page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("jue 12")).click();
        page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("10:00").setExact(true)).click();
        page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Reservar: Jueves 12 de marzo, 10:00"))
                .click();

        assertThat(page).hasURL(Pattern.compile("/portal/appointments$"));
        assertThat(page.getByText("Turno reservado: Jueves 12 de marzo, 10:00 con Dra. Arias. Te mandamos la "
                + "confirmación por email.")).isVisible();
        assertThat(page.getByRole(AriaRole.ARTICLE)).containsText("Jueves 12 de marzo, 10:00");
        assertThat(page.getByRole(AriaRole.ARTICLE)).containsText("Próximo");
        mockMvc.perform(get("/api/appointments").with(RECEPTION).param("practitionerId", arias.id().toString())
                        .param("date", "2026-03-12"))
                .andExpect(jsonPath("$[0].patientId").value(patient.id()))
                .andExpect(jsonPath("$[0].bookedVia").value("WEB"));
    }
}
