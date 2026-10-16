package com.clinic.schedule.shared.webapp;

import static com.clinic.schedule.portal.PatientAccountFixture.address;
import static com.clinic.schedule.portal.PatientAccountFixture.lastTokenTo;
import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

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
import org.springframework.test.web.servlet.MockMvc;

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
 * An email's address opening the patients' application (D-110), from end to end: the link of an activation sent
 * before the application existed — `/portal/activation?token=` — opens the same path on it, on a phone, where the
 * patient chooses their password and logs in with their document. The application as Maven built it, on the tests'
 * database and clock, driven in Chromium.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@Import(KeptAccountMail.class)
class PatientActivationE2EIT {

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

        assertThat(page).hasURL(at("/portal/app/activation?token=" + token));
        assertThat(page.getByRole(AriaRole.HEADING, new Page.GetByRoleOptions().setName("Activá tu cuenta"))).isVisible();
        page.getByLabel("Contraseña").fill("una-clave-del-telefono");
        page.getByLabel("Repetila").fill("una-clave-del-telefono");
        page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Activar")).click();

        assertThat(page).hasURL(Pattern.compile("/portal/app/login\\?activated$"));
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
}
