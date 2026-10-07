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
}
