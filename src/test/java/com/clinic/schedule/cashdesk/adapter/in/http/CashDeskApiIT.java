package com.clinic.schedule.cashdesk.adapter.in.http;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import com.clinic.schedule.appointment.PatientFixture;
import com.clinic.schedule.appointment.PractitionerFixture;
import com.clinic.schedule.appointment.PractitionerFixture.CreatedPractitioner;
import com.clinic.schedule.clinicalrecord.PractitionerUserFixture;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * The cash desk over the real stack (D-87): reception charges each visit as it puts the patient into a
 * queue, in the same transaction, or the patient does not come in; an emergency comes in uncharged and is
 * charged later; and the day's cash desk is read whole by reception, without the patients by the admins,
 * and not at all by the practitioners. "Now" is 10 March 2026 at midnight. The tests share a database, so
 * each looks for its own visits and compares totals before and after.
 */
@SpringBootTest
@AutoConfigureMockMvc
@WithMockUser(username = "recepcion", roles = "RECEPTION")
class CashDeskApiIT {

    private static final RequestPostProcessor ADMIN = user("admin").roles("ADMIN");
    private static final RequestPostProcessor OTHER_RECEPTIONIST = user("otra").roles("RECEPTION");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper json;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private UserDetailsService userDetails;

    private PatientFixture patients;
    private CreatedPractitioner arias;
    private RequestPostProcessor asArias;
    private String queue;

    @BeforeEach
    void setUp() throws Exception {
        patients = new PatientFixture(mockMvc, json);
        arias = new PractitionerFixture(mockMvc, json).workingEveryDay();
        asArias = new PractitionerUserFixture(jdbc, userDetails).userFor(arias.id());
        queue = read(mockMvc.perform(post("/api/queues").contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\": \"Caja " + UUID.randomUUID() + "\"}"))).get("id").asText();
        mockMvc.perform(post("/api/queues/" + queue + "/practitioners").contentType(MediaType.APPLICATION_JSON)
                .content("{\"practitionerId\": \"" + arias.id() + "\"}")).andExpect(status().isOk());
        mockMvc.perform(post("/api/queues/" + queue + "/open")).andExpect(status().isOk());
    }

    private JsonNode read(ResultActions result) throws Exception {
        return json.readTree(result.andReturn().getResponse().getContentAsString());
    }

    private ResultActions arrives(UUID patient, String body) throws Exception {
        return mockMvc.perform(post("/api/queues/" + queue + "/session/patients").contentType(MediaType.APPLICATION_JSON)
                .content("{\"patientId\": \"" + patient + "\"" + body + "}"));
    }

    /** The patient's entry in this queue: their visit. */
    private String visitOf(UUID patient) throws Exception {
        for (JsonNode entry : read(mockMvc.perform(get("/api/queues/" + queue + "/session"))).get("waiting")) {
            if (entry.get("patientId").asText().equals(patient.toString())) {
                return entry.get("id").asText();
            }
        }
        throw new AssertionError("The patient is not waiting");
    }

    /** The visit's first charge recorded that day. */
    private JsonNode movementOf(JsonNode day, String visit) {
        return movementOf(day, visit, "CHARGE", 0);
    }

    /** The visit's movement of that kind recorded that day, by its place among them. */
    private JsonNode movementOf(JsonNode day, String visit, String kind, int place) {
        int seen = 0;
        for (JsonNode movement : day.get("movements")) {
            if (movement.get("visitId").asText().equals(visit) && movement.get("kind").asText().equals(kind)
                    && seen++ == place) {
                return movement;
            }
        }
        return null;
    }

    private boolean isPending(JsonNode day, String visit) {
        for (JsonNode pending : day.get("pending")) {
            if (pending.get("visitId").asText().equals(visit)) {
                return true;
            }
        }
        return false;
    }

    private JsonNode today() throws Exception {
        return read(mockMvc.perform(get("/api/cash-desk")).andExpect(status().isOk()));
    }

    private int countFor(UUID patient, String table) {
        return jdbc.queryForObject("select count(*) from " + table + " where patient_id = ?", Integer.class, patient);
    }

    @Test
    void aVisitIsChargedInTheSameStepThePatientComesIn() throws Exception {
        UUID ana = patients.newPatient();

        arrives(ana, ", \"amount\": 15000.5").andExpect(status().isCreated());

        String visit = visitOf(ana);
        JsonNode day = today();
        JsonNode movement = movementOf(day, visit);
        assertThat(day.get("date").asText()).isEqualTo("2026-03-10");
        assertThat(movement).isNotNull();
        assertThat(movement.get("amount").decimalValue()).isEqualByComparingTo("15000.50");
        assertThat(movement.get("means").asText()).isEqualTo("CASH");
        assertThat(movement.get("recordedBy").asText()).isEqualTo("recepcion");
        assertThat(movement.get("recordedAt").asText()).isEqualTo("2026-03-10T00:00:00");
        assertThat(movement.get("emergency").asBoolean()).isFalse();
        assertThat(movement.get("patient").get("id").asText()).isEqualTo(ana.toString());
        assertThat(movement.get("patient").get("fullName").asText())
                .isEqualTo(jdbc.queryForObject("select full_name from patient where id = ?", String.class, ana));
        assertThat(isPending(day, visit)).isFalse();
    }

    @Test
    void withoutAChargeThePatientDoesNotComeIn() throws Exception {
        UUID ana = patients.newPatient();

        arrives(ana, "").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("A visit that is not an emergency is charged when the patient comes in"));

        assertThat(read(mockMvc.perform(get("/api/queues/" + queue + "/session"))).get("waiting")).isEmpty();
        assertThat(countFor(ana, "cash_visit")).isZero();
        assertThat(jdbc.queryForObject("select count(*) from arrival_event where payload like ?", Integer.class,
                "%" + ana + "%")).isZero();
    }

    @Test
    void aPatientWhoGivesNoNameIsNotRegisteredWhenTheChargeIsRefused() throws Exception {
        int before = jdbc.queryForObject("select count(*) from patient where alias is not null", Integer.class);

        mockMvc.perform(post("/api/queues/" + queue + "/session/unidentified")).andExpect(status().isBadRequest());

        assertThat(jdbc.queryForObject("select count(*) from patient where alias is not null", Integer.class))
                .isEqualTo(before);

        mockMvc.perform(post("/api/queues/" + queue + "/session/unidentified").contentType(MediaType.APPLICATION_JSON)
                .content("{\"amount\": 9000}")).andExpect(status().isCreated());
        UUID nameless = UUID.fromString(read(mockMvc.perform(get("/api/queues/" + queue + "/session")))
                .get("waiting").get(0).get("patientId").asText());
        assertThat(jdbc.queryForObject("select amount from cash_charge c join cash_visit v on v.id = c.visit_id "
                + "where v.patient_id = ?", BigDecimal.class, nameless)).isEqualByComparingTo("9000");
    }

    @Test
    void aPatientWithAnAppointmentPaysToComeInToo() throws Exception {
        UUID ana = patients.newPatient();
        String appointment = read(mockMvc.perform(post("/api/appointments").contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"patientId": "%s", "practitionerId": "%s", "reasonId": "%s", "startsAt": "2026-03-10T10:00:00"}
                        """.formatted(ana, arias.id(), arias.consulta())))
                .andExpect(status().isCreated())).get("id").asText();

        mockMvc.perform(post("/api/appointment-arrivals").contentType(MediaType.APPLICATION_JSON)
                .content("{\"appointmentId\": \"" + appointment + "\"}")).andExpect(status().isBadRequest());

        assertThat(read(mockMvc.perform(get("/api/queues/" + queue + "/session"))).get("waiting")).isEmpty();
        assertThat(countFor(ana, "cash_visit")).isZero();

        mockMvc.perform(post("/api/appointment-arrivals").contentType(MediaType.APPLICATION_JSON)
                .content("{\"appointmentId\": \"" + appointment + "\", \"amount\": 12000}"))
                .andExpect(status().isCreated());
        assertThat(movementOf(today(), visitOf(ana)).get("amount").decimalValue()).isEqualByComparingTo("12000");
    }

    @Test
    void theAmountIsPesosAndCentsAboveZeroAndUpToTheCeiling() throws Exception {
        UUID ana = patients.newPatient();

        arrives(ana, ", \"amount\": 0").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("An amount is greater than zero, with two decimals at most"));
        arrives(ana, ", \"amount\": 10.001").andExpect(status().isBadRequest());
        arrives(ana, ", \"amount\": 100000000").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("The amount may not be above 99999999.99"));
        assertThat(countFor(ana, "cash_visit")).isZero();

        arrives(ana, ", \"amount\": 99999999.99").andExpect(status().isCreated());
        assertThat(movementOf(today(), visitOf(ana)).get("amount").decimalValue())
                .isEqualByComparingTo("99999999.99");
    }

    @Test
    void anEmergencyComesInUnchargedAndIsChargedLaterOnce() throws Exception {
        UUID ana = patients.newPatient();

        arrives(ana, ", \"emergency\": true").andExpect(status().isCreated());

        String visit = visitOf(ana);
        assertThat(isPending(today(), visit)).isTrue();
        assertThat(movementOf(today(), visit)).isNull();

        mockMvc.perform(post("/api/visits/" + visit + "/charge").contentType(MediaType.APPLICATION_JSON)
                .content("{}")).andExpect(status().isBadRequest());
        JsonNode day = read(mockMvc.perform(post("/api/visits/" + visit + "/charge").with(OTHER_RECEPTIONIST)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"amount\": 5000}"))
                .andExpect(status().isOk()));

        assertThat(isPending(day, visit)).isFalse();
        JsonNode movement = movementOf(day, visit);
        assertThat(movement.get("amount").decimalValue()).isEqualByComparingTo("5000");
        assertThat(movement.get("emergency").asBoolean()).isTrue();
        assertThat(movement.get("recordedBy").asText()).isEqualTo("otra");

        mockMvc.perform(post("/api/visits/" + visit + "/charge").contentType(MediaType.APPLICATION_JSON)
                .content("{\"amount\": 5000}")).andExpect(status().isConflict());
        mockMvc.perform(post("/api/visits/" + UUID.randomUUID() + "/charge").contentType(MediaType.APPLICATION_JSON)
                .content("{\"amount\": 5000}")).andExpect(status().isNotFound());
        assertThat(jdbc.queryForObject("select count(*) from cash_charge where visit_id = ?", Integer.class,
                UUID.fromString(visit))).isOne();
    }

    @Test
    void anEmergencyMayPayAsItComesIn() throws Exception {
        UUID ana = patients.newPatient();

        arrives(ana, ", \"emergency\": true, \"amount\": 7000").andExpect(status().isCreated());

        JsonNode day = today();
        assertThat(movementOf(day, visitOf(ana))).isNotNull();
        assertThat(isPending(day, visitOf(ana))).isFalse();
    }

    @Test
    void theTotalsAddUpWhatEachUserRecorded() throws Exception {
        JsonNode before = today();

        arrives(patients.newPatient(), ", \"amount\": 1000").andExpect(status().isCreated());
        arrives(patients.newPatient(), ", \"amount\": 2500.25").andExpect(status().isCreated());
        arrives(patients.newPatient(), ", \"emergency\": true").andExpect(status().isCreated());

        JsonNode after = today();
        assertThat(after.get("total").get("charged").get("count").asInt())
                .isEqualTo(before.get("total").get("charged").get("count").asInt() + 2);
        assertThat(total(after, "charged").subtract(total(before, "charged"))).isEqualByComparingTo("3500.25");
        assertThat(after.get("total").get("left").decimalValue().subtract(before.get("total").get("left").decimalValue()))
                .isEqualByComparingTo("3500.25");
        assertThat(recorded(after, "recepcion").subtract(recorded(before, "recepcion"))).isEqualByComparingTo("3500.25");
    }

    private BigDecimal recorded(JsonNode day, String user) {
        return recorded(day, user, "charged");
    }

    /** What the user recorded that day of a kind: charged or refunded, as a sum, or what is left. */
    private BigDecimal recorded(JsonNode day, String user, String what) {
        for (JsonNode each : day.get("byRecorder")) {
            if (each.get("recordedBy").asText().equals(user)) {
                JsonNode value = each.get(what);
                return value.isObject() ? value.get("amount").decimalValue() : value.decimalValue();
            }
        }
        return BigDecimal.ZERO;
    }

    private BigDecimal total(JsonNode day, String what) {
        return day.get("total").get(what).get("amount").decimalValue();
    }

    @Test
    void eachWayInChargesByTheMeansReceptionSaysAndWithoutOneInCash() throws Exception {
        UUID ana = patients.newPatient();
        UUID juan = patients.newPatient();
        UUID eva = patients.newPatient();
        UUID luis = patients.newPatient();
        String appointment = read(mockMvc.perform(post("/api/appointments").contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"patientId": "%s", "practitionerId": "%s", "reasonId": "%s", "startsAt": "2026-03-10T11:00:00"}
                        """.formatted(eva, arias.id(), arias.consulta())))
                .andExpect(status().isCreated())).get("id").asText();

        arrives(ana, ", \"amount\": 100, \"means\": \"CHEQUE\"").andExpect(status().isBadRequest());
        assertThat(countFor(ana, "cash_visit")).isZero();
        arrives(ana, ", \"amount\": 100, \"means\": \"DEBIT\"").andExpect(status().isCreated());
        arrives(juan, ", \"amount\": 100").andExpect(status().isCreated());
        mockMvc.perform(post("/api/appointment-arrivals").contentType(MediaType.APPLICATION_JSON)
                .content("{\"appointmentId\": \"" + appointment + "\", \"amount\": 100, \"means\": \"TRANSFER\"}"))
                .andExpect(status().isCreated());
        arrives(luis, ", \"emergency\": true, \"means\": \"QR\"").andExpect(status().isCreated());
        String later = visitOf(luis);
        assertThat(isPending(today(), later)).isTrue();
        mockMvc.perform(post("/api/visits/" + later + "/charge").contentType(MediaType.APPLICATION_JSON)
                .content("{\"amount\": 100, \"means\": \"CREDIT\"}")).andExpect(status().isOk());
        mockMvc.perform(post("/api/queues/" + queue + "/session/unidentified").contentType(MediaType.APPLICATION_JSON)
                .content("{\"amount\": 100, \"means\": \"QR\"}")).andExpect(status().isCreated());
        UUID nameless = null;
        for (JsonNode entry : read(mockMvc.perform(get("/api/queues/" + queue + "/session"))).get("waiting")) {
            UUID patient = UUID.fromString(entry.get("patientId").asText());
            if (!Set.of(ana, juan, eva, luis).contains(patient)) {
                nameless = patient;
            }
        }

        JsonNode day = today();
        assertThat(movementOf(day, visitOf(ana)).get("means").asText()).isEqualTo("DEBIT");
        assertThat(movementOf(day, visitOf(juan)).get("means").asText()).isEqualTo("CASH");
        assertThat(movementOf(day, visitOf(eva)).get("means").asText()).isEqualTo("TRANSFER");
        assertThat(movementOf(day, later).get("means").asText()).isEqualTo("CREDIT");
        assertThat(movementOf(day, visitOf(nameless)).get("means").asText()).isEqualTo("QR");
    }

    @Test
    void theTotalsAreSplitByMeansAndOnlyCashCountsForTheDrawer() throws Exception {
        JsonNode before = today();
        JsonNode logBefore = read(mockMvc.perform(get("/api/cash-desk-log").with(ADMIN)));
        UUID ana = patients.newPatient();

        arrives(patients.newPatient(), ", \"amount\": 1000, \"means\": \"CASH\"").andExpect(status().isCreated());
        arrives(ana, ", \"amount\": 3000, \"means\": \"DEBIT\"").andExpect(status().isCreated());
        JsonNode day = read(refund(visitOf(ana), "{\"amount\": 1000, \"reason\": \"cobrado de más\"}")
                .andExpect(status().isOk()));

        assertThat(movementOf(day, visitOf(ana), "REFUND", 0).get("means").asText()).isEqualTo("DEBIT");
        assertThat(byMeans(day.get("total"), "DEBIT", "left")
                .subtract(byMeans(before.get("total"), "DEBIT", "left"))).isEqualByComparingTo("2000");
        assertThat(byMeans(day.get("total"), "DEBIT", "refunded")
                .subtract(byMeans(before.get("total"), "DEBIT", "refunded"))).isEqualByComparingTo("1000");
        assertThat(byMeans(day.get("total"), "CASH", "charged")
                .subtract(byMeans(before.get("total"), "CASH", "charged"))).isEqualByComparingTo("1000");
        assertThat(day.get("total").get("drawer").decimalValue()
                .subtract(before.get("total").get("drawer").decimalValue())).isEqualByComparingTo("1000");
        assertThat(day.get("total").get("left").decimalValue()
                .subtract(before.get("total").get("left").decimalValue())).isEqualByComparingTo("3000");
        assertThat(recorded(day, "recepcion", "drawer").subtract(recorded(before, "recepcion", "drawer")))
                .isEqualByComparingTo("1000");
        assertThat(byMeans(recorder(day, "recepcion"), "DEBIT", "left")
                .subtract(byMeans(recorder(before, "recepcion"), "DEBIT", "left"))).isEqualByComparingTo("2000");

        JsonNode log = read(mockMvc.perform(get("/api/cash-desk-log").with(ADMIN)).andExpect(status().isOk()));
        assertThat(log.get("total").get("drawer").decimalValue()
                .subtract(logBefore.get("total").get("drawer").decimalValue())).isEqualByComparingTo("1000");
        assertThat(byMeans(log.get("total"), "DEBIT", "left")
                .subtract(byMeans(logBefore.get("total"), "DEBIT", "left"))).isEqualByComparingTo("2000");
        assertThat(byMeans(recorder(log, "recepcion"), "DEBIT", "left")
                .subtract(byMeans(recorder(logBefore, "recepcion"), "DEBIT", "left"))).isEqualByComparingTo("2000");
        assertThat(recorded(log, "recepcion", "drawer").subtract(recorded(logBefore, "recepcion", "drawer")))
                .isEqualByComparingTo("1000");
    }

    /** What a total, or a user's, says of one means: charged or refunded, as a sum, or what is left; zero if none. */
    private static BigDecimal byMeans(JsonNode total, String means, String what) {
        if (total == null) {
            return BigDecimal.ZERO;
        }
        for (JsonNode each : total.get("byMeans")) {
            if (each.get("means").asText().equals(means)) {
                JsonNode value = each.get(what);
                return value.isObject() ? value.get("amount").decimalValue() : value.decimalValue();
            }
        }
        return BigDecimal.ZERO;
    }

    private static JsonNode recorder(JsonNode day, String user) {
        for (JsonNode each : day.get("byRecorder")) {
            if (each.get("recordedBy").asText().equals(user)) {
                return each;
            }
        }
        return null;
    }

    @Test
    void anotherDayHasItsOwnCashDesk() throws Exception {
        arrives(patients.newPatient(), ", \"amount\": 1000").andExpect(status().isCreated());

        mockMvc.perform(get("/api/cash-desk").param("date", "2026-03-09")).andExpect(status().isOk())
                .andExpect(jsonPath("$.date").value("2026-03-09"))
                .andExpect(jsonPath("$.movements").isEmpty())
                .andExpect(jsonPath("$.total.charged.count").value(0));
    }

    @Test
    void theAdminsReadTheDayWithoutItsPatientsNorItsPendingVisits() throws Exception {
        UUID ana = patients.newPatient();
        arrives(ana, ", \"amount\": 4321").andExpect(status().isCreated());
        arrives(patients.newPatient(), ", \"emergency\": true").andExpect(status().isCreated());

        String body = mockMvc.perform(get("/api/cash-desk-log").with(ADMIN)).andExpect(status().isOk())
                .andExpect(jsonPath("$.date").value("2026-03-10"))
                .andExpect(jsonPath("$.movements[0].means").value("CASH"))
                .andExpect(jsonPath("$.total.charged.count").isNumber())
                .andExpect(jsonPath("$.byRecorder[0].recordedBy").exists())
                .andReturn().getResponse().getContentAsString();

        assertThat(body).contains("4321").doesNotContain("patient").doesNotContain("pending")
                .doesNotContain(ana.toString()).doesNotContain("visitId").doesNotContain("emergency");
        mockMvc.perform(get("/api/cash-desk-log").param("date", "2026-03-09").with(ADMIN)).andExpect(status().isOk())
                .andExpect(jsonPath("$.date").value("2026-03-09"))
                .andExpect(jsonPath("$.movements").isEmpty());
        mockMvc.perform(get("/api/cash-desk").with(ADMIN)).andExpect(status().isForbidden());
        mockMvc.perform(post("/api/visits/" + UUID.randomUUID() + "/charge").with(ADMIN)
                .contentType(MediaType.APPLICATION_JSON).content("{\"amount\": 1}")).andExpect(status().isForbidden());
    }

    @Test
    void practitionersReadNoneOfIt() throws Exception {
        mockMvc.perform(get("/api/cash-desk").with(asArias)).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/cash-desk-log").with(asArias)).andExpect(status().isForbidden());
        mockMvc.perform(post("/api/visits/" + UUID.randomUUID() + "/charge").with(asArias)
                .contentType(MediaType.APPLICATION_JSON).content("{\"amount\": 1}")).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/cash-desk-log")).andExpect(status().isForbidden());
    }

    private ResultActions refund(String visit, String body) throws Exception {
        return mockMvc.perform(post("/api/visits/" + visit + "/refund").contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    @Test
    void someOrAllOfAChargeIsGivenBackAsAMovementOfItsOwnAndTheVisitIsChargedAgain() throws Exception {
        UUID ana = patients.newPatient();
        arrives(ana, ", \"amount\": 15000").andExpect(status().isCreated());
        String visit = visitOf(ana);
        JsonNode before = today();

        refund(visit, "{\"amount\": 5000}").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("A refund says why"));
        refund(visit, "{\"amount\": 5000, \"reason\": \"" + "x".repeat(201) + "\"}")
                .andExpect(status().isBadRequest());
        refund(visit, "{\"amount\": 0, \"reason\": \"error\"}").andExpect(status().isBadRequest());
        JsonNode day = read(mockMvc.perform(post("/api/visits/" + visit + "/refund").with(OTHER_RECEPTIONIST)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\": 5000, \"reason\": \" cobrado de m\u00e1s \"}"))
                .andExpect(status().isOk()));

        JsonNode given = movementOf(day, visit, "REFUND", 0);
        assertThat(given.get("amount").decimalValue()).isEqualByComparingTo("5000");
        assertThat(given.get("reason").asText()).isEqualTo("cobrado de m\u00e1s");
        assertThat(given.get("recordedBy").asText()).isEqualTo("otra");
        assertThat(given.get("means").asText()).isEqualTo("CASH");
        assertThat(given.get("left").isNull()).isTrue();
        assertThat(given.get("patient").get("id").asText()).isEqualTo(ana.toString());
        assertThat(movementOf(day, visit).get("left").decimalValue()).isEqualByComparingTo("10000");
        assertThat(movementOf(day, visit).get("reason").isNull()).isTrue();
        assertThat(total(day, "refunded").subtract(total(before, "refunded"))).isEqualByComparingTo("5000");
        assertThat(day.get("total").get("refunded").get("count").asInt())
                .isEqualTo(before.get("total").get("refunded").get("count").asInt() + 1);
        assertThat(before.get("total").get("left").decimalValue().subtract(day.get("total").get("left").decimalValue()))
                .isEqualByComparingTo("5000");
        assertThat(recorded(day, "otra", "refunded").subtract(recorded(before, "otra", "refunded")))
                .isEqualByComparingTo("5000");

        refund(visit, "{\"amount\": 10000.01, \"reason\": \"se fue\"}").andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("A refund may not be above what is left of the charge, 10000.00"));
        mockMvc.perform(post("/api/visits/" + visit + "/charge").contentType(MediaType.APPLICATION_JSON)
                .content("{\"amount\": 1}")).andExpect(status().isConflict());
        refund(visit, "{\"amount\": 10000, \"reason\": \"importe equivocado\"}").andExpect(status().isOk());
        refund(visit, "{\"amount\": 1, \"reason\": \"error\"}").andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("There is nothing left to give back"));

        day = read(mockMvc.perform(post("/api/visits/" + visit + "/charge").contentType(MediaType.APPLICATION_JSON)
                .content("{\"amount\": 1500}")).andExpect(status().isOk()));
        assertThat(movementOf(day, visit, "CHARGE", 1).get("amount").decimalValue()).isEqualByComparingTo("1500");
        assertThat(movementOf(day, visit, "CHARGE", 1).get("left").decimalValue()).isEqualByComparingTo("1500");
        assertThat(movementOf(day, visit).get("left").decimalValue()).isEqualByComparingTo("0");
        assertThat(jdbc.queryForObject("select count(*) from cash_refund r join cash_charge c on c.id = r.charge_id "
                + "where c.visit_id = ?", Integer.class, UUID.fromString(visit))).isEqualTo(2);
        refund(UUID.randomUUID().toString(), "{\"amount\": 1, \"reason\": \"error\"}").andExpect(status().isNotFound());
    }

    @Test
    void nothingIsGivenBackOfAnEmergencyNeverCharged() throws Exception {
        UUID ana = patients.newPatient();
        arrives(ana, ", \"emergency\": true").andExpect(status().isCreated());

        refund(visitOf(ana), "{\"amount\": 1, \"reason\": \"error\"}").andExpect(status().isConflict());
        assertThat(isPending(today(), visitOf(ana))).isTrue();
    }

    @Test
    void aRefundCountsTheDayItIsRecordedWhateverDayItsChargeWas() throws Exception {
        UUID visit = UUID.randomUUID();
        UUID charge = UUID.randomUUID();
        jdbc.update("insert into cash_visit (id, patient_id, arrived_at, emergency) values (?, ?, ?, false)", visit,
                patients.newPatient(), java.sql.Timestamp.valueOf("2026-03-09 18:00:00"));
        jdbc.update("insert into cash_charge (id, visit_id, charge_no, amount, means, recorded_by, recorded_at) "
                + "values (?, ?, 1, 3000, 'CASH', 'recepcion', ?)", charge, visit,
                java.sql.Timestamp.valueOf("2026-03-09 18:00:00"));

        JsonNode day = read(refund(visit.toString(), "{\"amount\": 3000, \"reason\": \"no fue atendido\"}")
                .andExpect(status().isOk()));

        assertThat(movementOf(day, visit.toString(), "REFUND", 0)).isNotNull();
        assertThat(movementOf(day, visit.toString())).isNull();
        JsonNode yesterday = read(mockMvc.perform(get("/api/cash-desk").param("date", "2026-03-09")));
        assertThat(movementOf(yesterday, visit.toString()).get("left").decimalValue()).isEqualByComparingTo("0");
        assertThat(movementOf(yesterday, visit.toString(), "REFUND", 0)).isNull();
    }

    @Test
    void theAdminsReadTheRefundsWithoutWhy() throws Exception {
        UUID ana = patients.newPatient();
        arrives(ana, ", \"amount\": 2222").andExpect(status().isCreated());
        refund(visitOf(ana), "{\"amount\": 1111, \"reason\": \"motivo-que-no-ven\"}").andExpect(status().isOk());

        String body = mockMvc.perform(get("/api/cash-desk-log").with(ADMIN)).andExpect(status().isOk())
                .andExpect(jsonPath("$.total.refunded.count").isNumber())
                .andExpect(jsonPath("$.total.left").isNumber())
                .andExpect(jsonPath("$.byRecorder[0].refunded").exists())
                .andExpect(jsonPath("$.movements[*].left").isEmpty())
                .andReturn().getResponse().getContentAsString();

        assertThat(body).contains("\"kind\":\"REFUND\"").contains("1111").doesNotContain("motivo-que-no-ven")
                .doesNotContain("reason");
        mockMvc.perform(post("/api/visits/" + visitOf(ana) + "/refund").with(ADMIN)
                .contentType(MediaType.APPLICATION_JSON).content("{\"amount\": 1, \"reason\": \"x\"}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/visits/" + visitOf(ana) + "/refund").with(asArias)
                .contentType(MediaType.APPLICATION_JSON).content("{\"amount\": 1, \"reason\": \"x\"}"))
                .andExpect(status().isForbidden());
    }
}
