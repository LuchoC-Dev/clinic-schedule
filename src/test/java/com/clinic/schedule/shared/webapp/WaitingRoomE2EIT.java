package com.clinic.schedule.shared.webapp;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
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
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.impl.driver.Driver;
import com.microsoft.playwright.options.AriaRole;

/**
 * The waiting room shows whom a practitioner called, from end to end (D-109): the staff's application as Maven built
 * it, on a port of its own, on the tests' database and clock, in a real browser — two, the screen on the wall and the
 * practitioner's. Two patients arrived into the practitioner's queue; the wall shows them waiting in order; the
 * practitioner calls the next, and the wall shows them highlighted, sent to the practitioner's room, with nothing
 * reloaded by hand.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@WithMockUser(username = "recepcion", roles = "RECEPTION")
class WaitingRoomE2EIT {

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

    private String at(String path) {
        return "http://localhost:" + port + path;
    }

    private String create(String path, String body) throws Exception {
        return json.readTree(mockMvc.perform(post(path).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString()).get("id").asText();
    }

    private static String unique() {
        return UUID.randomUUID().toString().substring(0, 6);
    }

    /** A patient with an appointment with the practitioner today, who arrived. */
    private void arrived(String fullName, String practitioner, String reason, String time) throws Exception {
        String patient = create("/api/patients", "{\"fullName\": \"" + fullName + "\"}");
        String appointment = create("/api/appointments", """
                {"patientId": "%s", "practitionerId": "%s", "reasonId": "%s", "startsAt": "2026-03-10T%s"}
                """.formatted(patient, practitioner, reason, time));
        new ArrivalFixture(mockMvc, jdbc).arrived(appointment);
    }

    private static void logIn(Page page, String username, String password) {
        page.getByLabel("Usuario").fill(username);
        page.getByLabel("Contraseña").fill(password);
        page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Ingresar")).click();
    }

    @Test
    void thePatientAPractitionerCallsIsShownHighlightedOnTheWall() throws Exception {
        String name = "Dra. Sala " + unique();
        String username = "sala-" + unique();
        String practitioner = create("/api/practitioners", "{\"fullName\": \"" + name + "\"}");
        String reason = create("/api/practitioners/" + practitioner + "/reasons", "{\"name\": \"consulta\", \"durationMinutes\": 30}");
        mockMvc.perform(put("/api/practitioners/" + practitioner + "/weekly-schedule").contentType(MediaType.APPLICATION_JSON)
                .content("{\"hours\": [{\"day\": \"TUESDAY\", \"startsAt\": \"07:00\", \"endsAt\": \"21:00\"}]}"))
                .andExpect(status().isOk());
        jdbc.update("insert into app_user (id, username, password_hash, role, practitioner_id, active) "
                + "values (?, ?, ?, 'PRACTITIONER', ?, true)", UUID.randomUUID(), username, PASSWORD_HASH,
                UUID.fromString(practitioner));
        String queueName = "Cola " + name;
        String queue = create("/api/queues", "{\"name\": \"" + queueName + "\", \"room\": \"7\"}");
        mockMvc.perform(post("/api/queues/" + queue + "/practitioners").contentType(MediaType.APPLICATION_JSON)
                .content("{\"practitionerId\": \"" + practitioner + "\"}")).andExpect(status().isOk());
        mockMvc.perform(post("/api/queues/" + queue + "/open")).andExpect(status().isOk());
        String first = "Tomás Pared " + unique();
        String second = "Camila Pared " + unique();
        arrived(first, practitioner, reason, "09:00:00");
        arrived(second, practitioner, reason, "09:30:00");

        try (BrowserContext wallBrowser = browser.newContext(); BrowserContext practitionerBrowser = browser.newContext()) {
            Page wall = wallBrowser.newPage();
            wall.navigate(at("/"));
            logIn(wall, "sala", "sala123");
            assertThat(wall).hasURL(Pattern.compile("/waiting-room$"));
            Locator card = wall.getByRole(AriaRole.REGION, new Page.GetByRoleOptions().setName(queueName));
            Locator waiting = card.getByRole(AriaRole.LIST);
            assertThat(waiting.getByRole(AriaRole.LISTITEM)).hasText(new String[] {first + "Sigue", second + "2°"});
            assertThat(card.getByRole(AriaRole.STATUS)).hasCount(0);
            assertThat(wall.getByRole(AriaRole.NAVIGATION)).hasCount(0);

            Page desk = practitionerBrowser.newPage();
            desk.navigate(at("/"));
            logIn(desk, username, "arias123");
            assertThat(desk).hasURL(Pattern.compile("/my-queue$"));
            desk.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Llamar al siguiente")).click();
            assertThat(desk.getByText("Atendiendo")).isVisible();

            Locator inside = card.getByRole(AriaRole.STATUS);
            assertThat(inside).hasText(first + "Pasá al consultorio 7");
            assertThat(inside).hasCSS("background-color", "rgb(15, 95, 91)");
            assertThat(waiting.getByRole(AriaRole.LISTITEM)).hasText(new String[] {second + "Sigue"});
            assertThat(card.getByText("1 esperando")).isVisible();
        }
    }
}
