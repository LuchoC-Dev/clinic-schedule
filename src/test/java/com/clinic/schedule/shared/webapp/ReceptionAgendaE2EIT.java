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
 * Reception's agenda of the day from end to end (D-97, D-98): the staff's application as Maven built it, served
 * by the application on a port of its own, on the tests' database and clock — "today" is Tuesday 10 March 2026 —,
 * driven in a real browser. Reception logs in, reads the day, chooses a practitioner and the next day, and logs
 * out; going back afterwards shows nothing of what was read.
 *
 * <p>Only Chromium, downloaded the first time and kept in Playwright's cache for every later run.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@WithMockUser(username = "recepcion", roles = "RECEPTION")
class ReceptionAgendaE2EIT {

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

    // --- the day's data, through the API, as reception -------------------------------------

    private String idOf(String response) throws Exception {
        return json.readTree(response).get("id").asText();
    }

    private String create(String path, String body) throws Exception {
        return idOf(mockMvc.perform(post(path).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString());
    }

    /** A practitioner with a name nobody else has, working every day, and the reason "consulta" of 30 minutes. */
    private String[] practitioner(String fullName) throws Exception {
        String id = create("/api/practitioners", "{\"fullName\": \"" + fullName + "\"}");
        String reason = create("/api/practitioners/" + id + "/reasons", "{\"name\": \"consulta\", \"durationMinutes\": 30}");
        StringBuilder week = new StringBuilder();
        for (String day : List.of("MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY", "SATURDAY", "SUNDAY")) {
            week.append(week.isEmpty() ? "" : ",")
                    .append("{\"day\": \"%s\", \"startsAt\": \"07:00\", \"endsAt\": \"21:00\"}".formatted(day));
        }
        mockMvc.perform(put("/api/practitioners/" + id + "/weekly-schedule").contentType(MediaType.APPLICATION_JSON)
                .content("{\"hours\": [" + week + "]}")).andExpect(status().isOk());
        return new String[] {id, reason};
    }

    private String book(String[] practitioner, String patientName, String startsAt) throws Exception {
        String patient = create("/api/patients", "{\"fullName\": \"" + patientName + "\"}");
        return create("/api/appointments", """
                {"patientId": "%s", "practitionerId": "%s", "reasonId": "%s", "startsAt": "%s"}
                """.formatted(patient, practitioner[0], practitioner[1], startsAt));
    }

    private static String unique() {
        return UUID.randomUUID().toString().substring(0, 6);
    }

    // --- the browser ---------------------------------------------------------------------------

    private void logInAsReception() {
        page.navigate(at("/app/"));
        assertThat(page).hasURL(Pattern.compile("/app/login$"));
        page.getByLabel("Usuario").fill("recepcion");
        page.getByLabel("Contraseña").fill("recepcion123");
        page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Ingresar")).click();
    }

    private Locator row(String patient) {
        return page.getByRole(AriaRole.ROW).filter(new Locator.FilterOptions().setHasText(patient));
    }

    @Test
    void receptionReadsTheDayChoosesAPractitionerAndTheNextDayAndLogsOutLeavingNothingBehind() throws Exception {
        String name = "Dra. Ensayo " + unique();
        String[] practitioner = practitioner(name);
        String today = "Rosa Hoy " + unique();
        String tomorrow = "Raúl Mañana " + unique();
        String elsewhere = "Otra Persona " + unique();
        String arrived = book(practitioner, today, "2026-03-10T09:00:00");
        book(practitioner, tomorrow, "2026-03-11T10:30:00");
        book(practitioner(name + " bis"), elsewhere, "2026-03-10T09:00:00");
        new ArrivalFixture(mockMvc, jdbc).arrived(arrived);

        logInAsReception();

        assertThat(page).hasURL(Pattern.compile("/app/agenda$"));
        assertThat(page.getByRole(AriaRole.HEADING, new Page.GetByRoleOptions().setName("Agenda del día"))).isVisible();
        assertThat(page.getByText("Recepción · recepcion")).isVisible();
        assertThat(page.getByText("martes 10 de marzo")).isVisible();
        assertThat(row(today)).isVisible();
        assertThat(row(elsewhere)).isVisible();

        page.getByText(name, new Page.GetByTextOptions().setExact(true)).click();
        assertThat(page).hasURL(Pattern.compile("/app/agenda\\?practitionerId=" + practitioner[0] + "$"));
        assertThat(row(today)).containsText("09:00");
        assertThat(row(today)).containsText(name);
        assertThat(row(today)).containsText("Llegó a las");
        assertThat(row(elsewhere)).hasCount(0);
        assertThat(page.getByText("1 turno · 1 llegó")).isVisible();

        page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Día siguiente")).click();
        assertThat(page.getByText("miércoles 11 de marzo")).isVisible();
        assertThat(row(tomorrow)).containsText("10:30");
        assertThat(row(tomorrow)).containsText("Reservado");
        assertThat(row(today)).hasCount(0);

        page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Salir")).click();
        assertThat(page).hasURL(Pattern.compile("/app/login\\?logout$"));
        assertThat(page.getByText("Sesión cerrada.")).isVisible();

        // Back, the page is asked of the server again, which knows nobody: nothing read comes back.
        page.goBack();
        assertThat(page).hasURL(Pattern.compile("/app/login$"));
        assertThat(page.getByLabel("Usuario")).isVisible();
        assertThat(page.getByText(tomorrow)).hasCount(0);
    }

    /**
     * The menu of reception, its own agenda first; a screen not built here yet is the Thymeleaf one — changing one's
     * password, every role's, once reception's screens are all here (D-107).
     */
    @Test
    void theMenuLeadsToTheScreensStillOnThymeleafWithTheSameSession() {
        logInAsReception();
        assertThat(page).hasURL(Pattern.compile("/app/agenda$"));

        page.getByRole(AriaRole.NAVIGATION).getByText("Contraseña").click();

        assertThat(page).hasURL(at("/password"));
        assertThat(page.getByRole(AriaRole.HEADING, new Page.GetByRoleOptions().setName("Cambiar la contraseña"))).isVisible();
        assertThat(page.locator("#root")).hasCount(0);
    }

    /**
     * Booking from the agenda (D-75, D-100): the practitioner's free times for the reason, a patient registered in the
     * same step, and the appointment on the agenda afterwards. Taking a time that is gone by then is refused in
     * Spanish, with the form as it was.
     */
    @Test
    void receptionBooksANewPatientAtAFreeTimeAndFindsThemOnTheAgenda() throws Exception {
        String name = "Dra. Reserva " + unique();
        String[] practitioner = practitioner(name);
        String taken = "Ya Reservado " + unique();
        book(practitioner, taken, "2026-03-12T09:00:00");
        String patient = "Rosa Nueva " + unique();

        logInAsReception();
        assertThat(page).hasURL(Pattern.compile("/app/agenda$"));
        page.navigate(at("/app/agenda?date=2026-03-12&practitionerId=" + practitioner[0]));
        assertThat(row(taken)).isVisible();
        page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Nuevo turno")).click();

        assertThat(page.getByRole(AriaRole.HEADING, new Page.GetByRoleOptions().setName("Nuevo turno con " + name)))
                .isVisible();
        Locator times = page.getByRole(AriaRole.GROUP, new Page.GetByRoleOptions().setName("Horarios libres"));
        assertThat(times.getByText("09:30", new Locator.GetByTextOptions().setExact(true))).isVisible();
        assertThat(times.getByText("09:00", new Locator.GetByTextOptions().setExact(true))).hasCount(0);
        times.getByText("10:00", new Locator.GetByTextOptions().setExact(true)).click();
        page.getByText("Paciente nuevo").click();
        page.getByLabel("Nombre completo").fill(patient);
        page.getByLabel("Teléfono").fill("1145556666");
        page.getByLabel("Nota (opcional)").fill("Primera vez");
        page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Reservar")).click();

        assertThat(page).hasURL(Pattern.compile("/app/agenda\\?date=2026-03-12&practitionerId=" + practitioner[0] + "$"));
        assertThat(page.getByRole(AriaRole.STATUS)).hasText("Paciente registrado y turno reservado.");
        assertThat(row(patient)).containsText("10:00");
        assertThat(row(patient)).containsText("Primera vez");
        assertThat(row(patient)).containsText("Reservado");

        // The same time again, now taken: refused, and nothing typed is lost.
        page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Nuevo turno")).click();
        page.getByText("Paciente nuevo").click();
        page.getByLabel("Nombre completo").fill("Otra Persona " + unique());
        page.getByLabel("…u otra hora").fill("10:00");
        page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Reservar")).click();
        assertThat(page.getByRole(AriaRole.ALERT)).hasText("El profesional ya tiene un turno en ese horario.");
        assertThat(page.getByLabel("…u otra hora")).hasValue("10:00");
    }

    /**
     * Charging (D-87, D-101): the patient arrives from the agenda, their visit charged as they come in; an amount with
     * one zero too many asks once more before it is sent; the day's cash desk shows it, and part of it is given back,
     * saying why.
     */
    @Test
    void receptionChargesAnArrivalConfirmingAVeryLargeAmountAndGivesPartOfItBack() throws Exception {
        String name = "Dra. Caja " + unique();
        String[] practitioner = practitioner(name);
        new ArrivalFixture(mockMvc, jdbc).openQueueFor(UUID.fromString(practitioner[0]));
        String patient = "Rosa Cobro " + unique();
        book(practitioner, patient, "2026-03-10T11:00:00");

        logInAsReception();
        assertThat(page).hasURL(Pattern.compile("/app/agenda$"));
        page.navigate(at("/app/agenda?practitionerId=" + practitioner[0]));
        row(patient).getByRole(AriaRole.BUTTON, new Locator.GetByRoleOptions().setName("Llegó")).click();
        Locator dialog = page.getByRole(AriaRole.DIALOG);
        dialog.getByLabel("Importe").fill("1500000");
        dialog.getByRole(AriaRole.BUTTON, new Locator.GetByRoleOptions().setName("Llegó")).click();
        assertThat(dialog.getByRole(AriaRole.ALERT)).containsText("Vas a cobrar $ 1.500.000,00");
        dialog.getByRole(AriaRole.BUTTON, new Locator.GetByRoleOptions().setName("Sí, cobrar $ 1.500.000,00")).click();

        assertThat(page.getByRole(AriaRole.STATUS)).hasText("Llegó: está en la cola de su profesional.");
        assertThat(row(patient)).containsText("Llegó a las");

        page.getByRole(AriaRole.NAVIGATION).getByText("Caja", new Locator.GetByTextOptions().setExact(true)).click();
        assertThat(page).hasURL(Pattern.compile("/app/cash-desk$"));
        Locator charge = page.getByRole(AriaRole.ROW).filter(new Locator.FilterOptions().setHasText(patient))
                .filter(new Locator.FilterOptions().setHasText("Cobro"));
        assertThat(charge).containsText("$ 1.500.000,00");
        charge.getByRole(AriaRole.BUTTON, new Locator.GetByRoleOptions().setName("Devolver (queda $ 1.500.000,00)")).click();
        dialog = page.getByRole(AriaRole.DIALOG);
        dialog.getByLabel("Importe a devolver").fill("500.000");
        dialog.getByLabel("Motivo").fill("cobrado de más");
        dialog.getByRole(AriaRole.BUTTON, new Locator.GetByRoleOptions().setName("Devolver")).click();

        assertThat(page.getByRole(AriaRole.STATUS)).hasText("Devolución registrada.");
        Locator refund = page.getByRole(AriaRole.ROW).filter(new Locator.FilterOptions().setHasText(patient))
                .filter(new Locator.FilterOptions().setHasText("Devolución"));
        assertThat(refund).containsText("−$ 500.000,00");
        assertThat(refund).containsText("cobrado de más");
        assertThat(charge.getByRole(AriaRole.BUTTON, new Locator.GetByRoleOptions().setName("Devolver (queda $ 1.000.000,00)")))
                .isVisible();
    }

    @Test
    void aWrongPasswordIsSaidAndNothingIsShown() {
        page.navigate(at("/app/agenda"));
        assertThat(page).hasURL(Pattern.compile("/app/login$"));
        page.getByLabel("Usuario").fill("recepcion");
        page.getByLabel("Contraseña").fill("no-es-la-clave");
        page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Ingresar")).click();

        assertThat(page.getByRole(AriaRole.ALERT)).hasText("Usuario o contraseña incorrectos.");
        assertThat(page.getByLabel("Usuario")).hasValue("recepcion");
    }
}
