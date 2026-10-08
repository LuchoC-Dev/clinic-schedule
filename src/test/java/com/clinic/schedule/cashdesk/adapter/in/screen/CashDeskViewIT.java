package com.clinic.schedule.cashdesk.adapter.in.screen;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import com.clinic.schedule.appointment.PatientFixture;
import com.clinic.schedule.appointment.PractitionerFixture;
import com.clinic.schedule.appointment.PractitionerFixture.CreatedPractitioner;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * The cash desk on the staff's screens (D-87): reception types the amount where it brings a patient in,
 * reads the day's cash desk and charges the pending emergencies; the admins read it without the patients.
 * "Today" is 10 March 2026, at midnight.
 */
@SpringBootTest
@AutoConfigureMockMvc
@WithMockUser(username = "recepcion", roles = "RECEPTION")
class CashDeskViewIT {

    private static final String DAY = "2026-03-10";
    private static final RequestPostProcessor ADMIN = user("admin").roles("ADMIN");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private ObjectMapper json;

    private PatientFixture patients;
    private CreatedPractitioner arias;
    private String queue;

    @BeforeEach
    void setUp() throws Exception {
        patients = new PatientFixture(mockMvc, json);
        arias = new PractitionerFixture(mockMvc, json).workingEveryDay();
        queue = json.readTree(mockMvc.perform(post("/api/queues").contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\": \"Caja " + UUID.randomUUID() + "\"}"))
                .andReturn().getResponse().getContentAsString()).get("id").asText();
        mockMvc.perform(post("/api/queues/" + queue + "/practitioners").contentType(MediaType.APPLICATION_JSON)
                .content("{\"practitionerId\": \"" + arias.id() + "\"}")).andExpect(status().isOk());
        mockMvc.perform(post("/api/queues/" + queue + "/open")).andExpect(status().isOk());
    }

    private String nameOf(UUID patient) {
        return jdbc.queryForObject("select full_name from patient where id = ?", String.class, patient);
    }

    private UUID visitOf(UUID patient) {
        return jdbc.queryForObject("select id from cash_visit where patient_id = ?", UUID.class, patient);
    }

    private String meansOf(UUID patient) {
        return jdbc.queryForObject("select c.means from cash_charge c join cash_visit v on v.id = c.visit_id "
                + "where v.patient_id = ?", String.class, patient);
    }

    private String appointmentFor(UUID patient, String at) throws Exception {
        return json.readTree(mockMvc.perform(post("/api/appointments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"patientId": "%s", "practitionerId": "%s", "reasonId": "%s", "startsAt": "%sT%s"}
                                """.formatted(patient, arias.id(), arias.consulta(), DAY, at)))
                .andReturn().getResponse().getContentAsString()).get("id").asText();
    }

    @Test
    void receptionTypesTheAmountWhereItBringsAPatientIn() throws Exception {
        UUID ana = patients.newPatient();
        mockMvc.perform(get("/arrivals").param("queueId", queue).param("query", nameOf(ana)))
                .andExpect(content().string(containsString("name=\"amount\"")))
                .andExpect(content().string(containsString("Importe $ (opcional)")));

        mockMvc.perform(post("/arrivals/" + queue + "/patients").with(csrf()).param("patientId", ana.toString()))
                .andExpect(flash().attribute("error", "Falta el importe del cobro: sin cobro, solo entra una urgencia."));
        mockMvc.perform(post("/arrivals/" + queue + "/patients").with(csrf()).param("patientId", ana.toString())
                        .param("amount", "0"))
                .andExpect(flash().attribute("error", "El importe debe ser mayor que cero, con dos decimales como mucho."));
        mockMvc.perform(post("/arrivals/" + queue + "/patients").with(csrf()).param("patientId", ana.toString())
                        .param("amount", "100000000"))
                .andExpect(flash().attribute("error", "El importe no puede superar $ 99.999.999,99."));
        mockMvc.perform(post("/arrivals/" + queue + "/unidentified").with(csrf()).param("amount", ""))
                .andExpect(flash().attribute("error", "Falta el importe del cobro: sin cobro, solo entra una urgencia."));

        mockMvc.perform(post("/arrivals/" + queue + "/patients").with(csrf()).param("patientId", ana.toString())
                        .param("amount", "15000.50"))
                .andExpect(flash().attribute("message", "Paciente sumado a la cola."));
        // A second charge, so each row's amount differs from every total.
        mockMvc.perform(post("/arrivals/" + queue + "/patients").with(csrf())
                        .param("patientId", patients.newPatient().toString()).param("amount", "1000.25"))
                .andExpect(flash().attribute("message", "Paciente sumado a la cola."));

        mockMvc.perform(get("/cash-desk"))
                .andExpect(content().string(containsString("$ 1.000,25")))
                .andExpect(content().string(containsString("Caja del día")))
                .andExpect(content().string(containsString(nameOf(ana))))
                .andExpect(content().string(containsString("$ 15.000,50")))
                .andExpect(content().string(containsString("Efectivo")));
    }

    @Test
    void anAppointmentArrivesFromTheAgendaWithItsCharge() throws Exception {
        UUID ana = patients.newPatient();
        String appointment = json.readTree(mockMvc.perform(post("/api/appointments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"patientId": "%s", "practitionerId": "%s", "reasonId": "%s", "startsAt": "%sT10:00:00"}
                                """.formatted(ana, arias.id(), arias.consulta(), DAY)))
                .andReturn().getResponse().getContentAsString()).get("id").asText();

        mockMvc.perform(get("/agenda").param("practitionerId", arias.id().toString()).param("date", DAY))
                .andExpect(content().string(containsString("name=\"amount\"")));
        mockMvc.perform(post("/arrivals/appointment-arrivals/" + appointment).with(csrf())
                        .param("practitionerId", arias.id().toString()).param("date", DAY))
                .andExpect(flash().attribute("error", "Falta el importe del cobro: sin cobro, solo entra una urgencia."));
        mockMvc.perform(post("/arrivals/" + queue + "/appointments/" + appointment).with(csrf()).param("amount", "8000"))
                .andExpect(flash().attribute("message", "Paciente con turno sumado a la cola de su profesional."));

        assertThat(jdbc.queryForObject("select c.amount from cash_charge c join cash_visit v on v.id = c.visit_id "
                + "where v.patient_id = ?", BigDecimal.class, ana)).isEqualByComparingTo("8000");
    }

    @Test
    void receptionChargesAPendingEmergencyFromTheCashDesk() throws Exception {
        UUID ana = patients.newPatient();
        mockMvc.perform(post("/arrivals/" + queue + "/patients").with(csrf()).param("patientId", ana.toString())
                        .param("emergency", "true").param("amount", ""))
                .andExpect(flash().attribute("message", "Paciente sumado como urgencia."));
        UUID visit = visitOf(ana);

        mockMvc.perform(get("/cash-desk"))
                .andExpect(content().string(containsString("Urgencias sin cobrar")))
                .andExpect(content().string(containsString("/cash-desk/visits/" + visit + "/charge")));

        mockMvc.perform(post("/cash-desk/visits/" + visit + "/charge").with(csrf()).param("date", DAY))
                .andExpect(redirectedUrl("/cash-desk?date=" + DAY))
                .andExpect(flash().attribute("error", "Falta el importe del cobro."));
        mockMvc.perform(post("/cash-desk/visits/" + visit + "/charge").with(csrf()).param("amount", "100000000"))
                .andExpect(flash().attribute("error", "El importe no puede superar $ 99.999.999,99."));
        mockMvc.perform(post("/cash-desk/visits/" + visit + "/charge").with(csrf()).param("amount", "-5"))
                .andExpect(flash().attribute("error", "El importe debe ser mayor que cero, con dos decimales como mucho."));
        mockMvc.perform(post("/cash-desk/visits/" + visit + "/charge").with(csrf()).param("amount", "5000"))
                .andExpect(redirectedUrl("/cash-desk"))
                .andExpect(flash().attribute("message", "Cobro registrado."));
        mockMvc.perform(post("/cash-desk/visits/" + visit + "/charge").with(csrf()).param("amount", "5000"))
                .andExpect(flash().attribute("error", "Esa visita ya está cobrada."));
        mockMvc.perform(post("/cash-desk/visits/" + UUID.randomUUID() + "/charge").with(csrf()).param("amount", "5000"))
                .andExpect(flash().attribute("error", "Esa visita no existe."));

        mockMvc.perform(get("/cash-desk").param("date", DAY))
                .andExpect(content().string(not(containsString("/cash-desk/visits/" + visit + "/charge"))))
                .andExpect(content().string(containsString("(urgencia)")));
    }

    @Test
    void receptionPicksTheMeansWhereverItCharges() throws Exception {
        UUID ana = patients.newPatient();
        UUID eva = patients.newPatient();
        UUID pablo = patients.newPatient();
        UUID juan = patients.newPatient();
        String evasAppointment = appointmentFor(eva, "11:00:00");
        String pablosAppointment = appointmentFor(pablo, "11:30:00");

        mockMvc.perform(get("/arrivals").param("queueId", queue).param("query", nameOf(ana)))
                .andExpect(content().string(containsString("name=\"means\"")))
                .andExpect(content().string(containsString("<option value=\"TRANSFER\">Transferencia</option>")));
        mockMvc.perform(get("/agenda").param("practitionerId", arias.id().toString()).param("date", DAY))
                .andExpect(content().string(containsString("name=\"means\"")));

        mockMvc.perform(post("/arrivals/" + queue + "/patients").with(csrf()).param("patientId", ana.toString())
                        .param("amount", "100").param("means", "DEBIT"))
                .andExpect(flash().attribute("message", "Paciente sumado a la cola."));
        mockMvc.perform(post("/arrivals/appointment-arrivals/" + evasAppointment).with(csrf())
                        .param("practitionerId", arias.id().toString()).param("date", DAY)
                        .param("amount", "100").param("means", "TRANSFER"))
                .andExpect(flash().attribute("message", "Llegó: está en la cola de su profesional."));
        mockMvc.perform(post("/arrivals/" + queue + "/appointments/" + pablosAppointment).with(csrf())
                        .param("amount", "100").param("means", "QR"))
                .andExpect(flash().attribute("message", "Paciente con turno sumado a la cola de su profesional."));
        int byQr = jdbc.queryForObject("select count(*) from cash_charge where means = 'QR'", Integer.class);
        mockMvc.perform(post("/arrivals/" + queue + "/unidentified").with(csrf()).param("amount", "100")
                        .param("means", "QR"))
                .andExpect(flash().attribute("message", "Paciente sin datos registrado y sumado a la cola."));
        mockMvc.perform(post("/arrivals/" + queue + "/patients").with(csrf()).param("patientId", juan.toString())
                .param("emergency", "true").param("amount", "").param("means", "CASH"));
        mockMvc.perform(get("/cash-desk")).andExpect(content().string(containsString("name=\"means\"")));
        mockMvc.perform(post("/cash-desk/visits/" + visitOf(juan) + "/charge").with(csrf()).param("amount", "100")
                        .param("means", "CREDIT"))
                .andExpect(flash().attribute("message", "Cobro registrado."));

        assertThat(meansOf(ana)).isEqualTo("DEBIT");
        assertThat(meansOf(eva)).isEqualTo("TRANSFER");
        assertThat(meansOf(pablo)).isEqualTo("QR");
        assertThat(jdbc.queryForObject("select count(*) from cash_charge where means = 'QR'", Integer.class))
                .isEqualTo(byQr + 1);
        assertThat(meansOf(juan)).isEqualTo("CREDIT");
        mockMvc.perform(get("/cash-desk")).andExpect(content().string(containsString("Crédito")));
    }

    @Test
    void theTotalsShowEachMeansAndWhatShouldBeInTheDrawer() throws Exception {
        // A receptionist of their own, so their totals are this test's alone.
        RequestPostProcessor cajero = user("cajero").roles("RECEPTION");
        UUID ana = patients.newPatient();
        mockMvc.perform(post("/arrivals/" + queue + "/patients").with(cajero).with(csrf())
                .param("patientId", patients.newPatient().toString()).param("amount", "7000").param("means", "CASH"));
        mockMvc.perform(post("/arrivals/" + queue + "/patients").with(cajero).with(csrf())
                .param("patientId", ana.toString()).param("amount", "3000").param("means", "DEBIT"));
        mockMvc.perform(post("/cash-desk/visits/" + visitOf(ana) + "/refund").with(cajero).with(csrf())
                .param("amount", "1000").param("reason", "cobrado de más"));
        // Their rows of the totals, with the screen's spacing left out: overall — with what should be in the
        // drawer, their cash alone —, and by each means they took.
        String totals = "<tr><td>cajero</td><td>2</td><td>$10.000,00</td><td>1</td><td>$1.000,00</td>"
                + "<td>$9.000,00</td><td>$7.000,00</td></tr>"
                + "<trclass=\"muted\"><td>·Efectivo</td><td>1</td><td>$7.000,00</td><td>0</td><td>$0,00</td>"
                + "<td>$7.000,00</td><td></td></tr>"
                + "<trclass=\"muted\"><td>·Débito</td><td>1</td><td>$3.000,00</td><td>1</td><td>$1.000,00</td>"
                + "<td>$2.000,00</td><td></td></tr>";

        assertThat(mockMvc.perform(get("/cash-desk")).andReturn().getResponse().getContentAsString()
                .replaceAll("\\s", "")).contains(totals).contains("<th>Enelcajón</th>");
        assertThat(mockMvc.perform(get("/cash-desk-log").with(ADMIN)).andReturn().getResponse().getContentAsString()
                .replaceAll("\\s", "")).contains(totals).contains("<th>Enelcajón</th>");
        mockMvc.perform(get("/cash-desk-log").with(ADMIN)).andExpect(content().string(containsString("Débito")));
    }

    @Test
    void theAdminsReadTheCashDeskWithoutThePatients() throws Exception {
        UUID ana = patients.newPatient();
        mockMvc.perform(post("/arrivals/" + queue + "/patients").with(csrf()).param("patientId", ana.toString())
                .param("amount", "4321.09"));
        UUID pending = patients.newPatient();
        mockMvc.perform(post("/arrivals/" + queue + "/patients").with(csrf()).param("patientId", pending.toString())
                .param("emergency", "true"));

        mockMvc.perform(get("/cash-desk-log").with(ADMIN)).andExpect(status().isOk())
                .andExpect(content().string(containsString("$ 4.321,09")))
                .andExpect(content().string(containsString("href=\"/cash-desk-log\"")))
                .andExpect(content().string(not(containsString(nameOf(ana)))))
                .andExpect(content().string(not(containsString(nameOf(pending)))))
                .andExpect(content().string(not(containsString("Urgencias sin cobrar"))));
        mockMvc.perform(get("/cash-desk").with(ADMIN)).andExpect(status().isForbidden());
        mockMvc.perform(get("/cash-desk").with(user("arias").roles("PRACTITIONER"))).andExpect(status().isForbidden());
        mockMvc.perform(get("/cash-desk-log").with(user("arias").roles("PRACTITIONER")))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/cash-desk-log")).andExpect(status().isForbidden());
        mockMvc.perform(get("/cash-desk")).andExpect(content().string(containsString("href=\"/cash-desk\"")));
    }

    @Test
    void receptionGivesBackFromAChargeAndChargesAgainFromTheCashDesk() throws Exception {
        UUID ana = patients.newPatient();
        mockMvc.perform(post("/arrivals/" + queue + "/patients").with(csrf()).param("patientId", ana.toString())
                .param("amount", "15000.37"));
        UUID visit = visitOf(ana);
        String refund = "/cash-desk/visits/" + visit + "/refund";

        mockMvc.perform(get("/cash-desk"))
                .andExpect(content().string(containsString("Devolver (queda $ 15.000,37)")))
                .andExpect(content().string(containsString("value=\"15000.37\"")));

        mockMvc.perform(post(refund).with(csrf()).param("amount", "5000").param("date", DAY))
                .andExpect(redirectedUrl("/cash-desk?date=" + DAY))
                .andExpect(flash().attribute("error", "Falta el motivo de la devolución."));
        mockMvc.perform(post(refund).with(csrf()).param("amount", "5000").param("reason", "x".repeat(201)))
                .andExpect(flash().attribute("error", "El motivo no puede superar los 200 caracteres."));
        mockMvc.perform(post(refund).with(csrf()).param("amount", "15000.38").param("reason", "se fue"))
                .andExpect(flash().attribute("error", "No se puede devolver más de lo que queda: $ 15.000,37."));
        mockMvc.perform(post(refund).with(csrf()).param("amount", "5000").param("reason", "cobrado de más"))
                .andExpect(redirectedUrl("/cash-desk"))
                .andExpect(flash().attribute("message", "Devolución registrada."));

        mockMvc.perform(get("/cash-desk"))
                .andExpect(content().string(containsString("Devolución")))
                .andExpect(content().string(containsString("— cobrado de más")))
                .andExpect(content().string(containsString("−$ 5.000,00")))
                .andExpect(content().string(containsString("Devolver (queda $ 10.000,37)")))
                .andExpect(content().string(not(containsString("/cash-desk/visits/" + visit + "/charge"))));

        mockMvc.perform(post(refund).with(csrf()).param("amount", "10000.37").param("reason", "importe equivocado"))
                .andExpect(flash().attribute("message", "Devolución registrada."));
        mockMvc.perform(post(refund).with(csrf()).param("amount", "1").param("reason", "error"))
                .andExpect(flash().attribute("error", "No queda nada por devolver de ese cobro."));
        mockMvc.perform(get("/cash-desk"))
                .andExpect(content().string(containsString("/cash-desk/visits/" + visit + "/charge")))
                .andExpect(content().string(containsString("Cobrar de nuevo")))
                .andExpect(content().string(not(containsString(refund))));

        mockMvc.perform(post("/cash-desk/visits/" + visit + "/charge").with(csrf()).param("amount", "1500.37"))
                .andExpect(flash().attribute("message", "Cobro registrado."));
        mockMvc.perform(get("/cash-desk"))
                .andExpect(content().string(containsString("Devolver (queda $ 1.500,37)")));
        mockMvc.perform(post("/cash-desk/visits/" + UUID.randomUUID() + "/refund").with(csrf()).param("amount", "1")
                        .param("reason", "error"))
                .andExpect(flash().attribute("error", "Esa visita no existe."));
    }

    @Test
    void theAdminsReadTheRefundsWithoutWhy() throws Exception {
        // A receptionist of their own, so their totals are this test's alone.
        RequestPostProcessor cajera = user("cajera").roles("RECEPTION");
        UUID ana = patients.newPatient();
        mockMvc.perform(post("/arrivals/" + queue + "/patients").with(cajera).with(csrf())
                .param("patientId", ana.toString()).param("amount", "7777"));
        mockMvc.perform(post("/cash-desk/visits/" + visitOf(ana) + "/refund").with(cajera).with(csrf())
                .param("amount", "777").param("reason", "motivo-que-no-ven"));
        // Their row of the totals, with the screen's spacing left out: charged, given back, and what is left.
        String totals = "<td>cajera</td><td>1</td><td>$7.777,00</td><td>1</td><td>$777,00</td><td>$7.000,00</td>";

        assertThat(mockMvc.perform(get("/cash-desk")).andReturn().getResponse().getContentAsString()
                .replaceAll("\\s", "")).contains(totals);
        assertThat(mockMvc.perform(get("/cash-desk-log").with(ADMIN)).andReturn().getResponse().getContentAsString()
                .replaceAll("\\s", "")).contains(totals);
        mockMvc.perform(get("/cash-desk-log").with(ADMIN))
                .andExpect(content().string(containsString("Devolución")))
                .andExpect(content().string(containsString("−$ 777,00")))
                .andExpect(content().string(containsString("Devuelto")))
                .andExpect(content().string(not(containsString("motivo-que-no-ven"))))
                .andExpect(content().string(not(containsString("Devolver"))));
    }
}
