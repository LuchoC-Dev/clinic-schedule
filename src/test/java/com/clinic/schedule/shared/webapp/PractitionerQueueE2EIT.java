package com.clinic.schedule.shared.webapp;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Map;
import java.util.UUID;
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
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import com.clinic.schedule.arrival.ArrivalFixture;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.impl.driver.Driver;
import com.microsoft.playwright.options.AriaRole;

/**
 * A practitioner sees a patient from their queue, from end to end (D-104): the staff's application as Maven built it,
 * on a port of its own, on the tests' database and clock — "today" is Tuesday 10 March 2026 —, in a real browser.
 * The patient arrived into the practitioner's queue; the practitioner logs in, lands on their queue, calls the next,
 * writes the consultation beside the patient's record, finds it there, and finishes with them.
 *
 * <p>Only Chromium, downloaded the first time and kept in Playwright's cache for every later run.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@WithMockUser(username = "recepcion", roles = "RECEPTION")
class PractitionerQueueE2EIT {

    /** The seeded practitioners' password, {@code arias123}, as the seed keeps it (README). */
    private static final String PASSWORD_HASH = "$2a$10$ASI7VdIeFdUGAcTsoXPAGOjobyWGk4.AW9JzZON4BV1gn.ycqBTCO";

    private static Playwright playwright;
    private static Browser browser;

    @LocalServerPort
    private int port;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbc;

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

    @BeforeEach
    void open() {
        page = browser.newContext().newPage();
    }

    @AfterEach
    void closePage() {
        page.context().close();
    }

    private String at(String path) {
        return "http://localhost:" + port + path;
    }

    private String create(String path, String body) throws Exception {
        return json.readTree(mockMvc.perform(post(path).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString()).get("id").asText();
    }

    /** A practitioner working all day today, with the reason "consulta", and a user of theirs to log in with. */
    private String[] practitionerWithAUser(String fullName, String username) throws Exception {
        String id = create("/api/practitioners", "{\"fullName\": \"" + fullName + "\"}");
        String reason = create("/api/practitioners/" + id + "/reasons", "{\"name\": \"consulta\", \"durationMinutes\": 30}");
        mockMvc.perform(put("/api/practitioners/" + id + "/weekly-schedule").contentType(MediaType.APPLICATION_JSON)
                .content("{\"hours\": [{\"day\": \"TUESDAY\", \"startsAt\": \"07:00\", \"endsAt\": \"21:00\"}]}"))
                .andExpect(status().isOk());
        jdbc.update("insert into app_user (id, username, password_hash, role, practitioner_id, active) "
                + "values (?, ?, ?, 'PRACTITIONER', ?, true)", UUID.randomUUID(), username, PASSWORD_HASH, UUID.fromString(id));
        return new String[] {id, reason};
    }

    private static String unique() {
        return UUID.randomUUID().toString().substring(0, 6);
    }

    @Test
    void aPractitionerCallsTheNextPatientWritesTheirConsultationAndFinishes() throws Exception {
        String name = "Dra. Cola " + unique();
        String username = "cola-" + unique();
        String[] practitioner = practitionerWithAUser(name, username);
        String patientName = "Tomás Suárez " + unique();
        String patient = create("/api/patients", "{\"fullName\": \"" + patientName + "\"}");
        String appointment = create("/api/appointments", """
                {"patientId": "%s", "practitionerId": "%s", "reasonId": "%s", "startsAt": "2026-03-10T09:00:00"}
                """.formatted(patient, practitioner[0], practitioner[1]));
        new ArrivalFixture(mockMvc, jdbc).arrived(appointment);

        page.navigate(at("/app/"));
        page.getByLabel("Usuario").fill(username);
        page.getByLabel("Contraseña").fill("arias123");
        page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Ingresar")).click();

        assertThat(page).hasURL(Pattern.compile("/app/my-queue$"));
        Locator queue = page.getByRole(AriaRole.REGION, new Page.GetByRoleOptions().setName("Mi cola"));
        assertThat(queue.getByText(patientName)).isVisible();
        assertThat(queue.getByText("Turno 09:00")).isVisible();

        queue.getByRole(AriaRole.BUTTON, new Locator.GetByRoleOptions().setName("Llamar al siguiente")).click();

        assertThat(queue.getByText("Atendiendo")).isVisible();
        Locator record = page.getByRole(AriaRole.REGION, new Page.GetByRoleOptions().setName("Historia clínica"));
        assertThat(record.getByRole(AriaRole.HEADING, new Locator.GetByRoleOptions().setName(patientName))).isVisible();
        assertThat(record.getByText("Todavía no hay nada registrado.")).isVisible();

        record.getByLabel("Motivo y evolución").fill("Cefalea de tres días. Tensión arterial 130/85.");
        record.getByRole(AriaRole.BUTTON, new Locator.GetByRoleOptions().setName("+ Receta")).click();
        record.getByLabel("Receta 1", new Locator.GetByLabelOptions().setExact(true)).fill("Paracetamol 1 g cada 8 horas");
        record.getByRole(AriaRole.BUTTON, new Locator.GetByRoleOptions().setName("Guardar consulta")).click();

        assertThat(record.getByRole(AriaRole.STATUS)).hasText("Consulta registrada.");
        assertThat(record.getByText("Consulta · " + name)).isVisible();
        assertThat(record.getByText("Cefalea de tres días. Tensión arterial 130/85.")).isVisible();
        assertThat(record.getByText("Paracetamol 1 g cada 8 horas")).isVisible();
        assertThat(record.getByLabel("Motivo y evolución")).hasValue("");
        assertThat(jdbc.queryForObject("select count(*) from consultation where patient_id = ?", Integer.class,
                UUID.fromString(patient))).isEqualTo(1);

        queue.getByRole(AriaRole.BUTTON, new Locator.GetByRoleOptions().setName("Terminar")).click();

        assertThat(page.getByText("Cuando llames a alguien, acá ves su historia clínica.")).isVisible();
        assertThat(queue.getByText("Atendiendo")).hasCount(0);
    }
}
