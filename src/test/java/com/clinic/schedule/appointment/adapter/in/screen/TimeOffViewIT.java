package com.clinic.schedule.appointment.adapter.in.screen;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.jdbc.core.JdbcTemplate;

import com.clinic.schedule.appointment.PatientFixture;
import com.clinic.schedule.appointment.PractitionerFixture;
import com.clinic.schedule.appointment.PractitionerFixture.CreatedPractitioner;
import com.clinic.schedule.arrival.ArrivalFixture;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Recording an absence from the practitioner screen (D-46): it goes back to the practitioner
 * saying what the absence did to the agenda, in Spanish.
 */
@SpringBootTest
@AutoConfigureMockMvc
@WithMockUser(roles = "RECEPTION")
class TimeOffViewIT {

    /** A Tuesday: the day the tests' clock stands on. */
    private static final String DAY = "2026-03-10";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private ObjectMapper json;

    private PractitionerFixture practitioners;
    private PatientFixture patients;

    @BeforeEach
    void setUp() {
        practitioners = new PractitionerFixture(mockMvc, json);
        patients = new PatientFixture(mockMvc, json);
    }

    private String book(CreatedPractitioner practitioner, String day, String time) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/appointments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"patientId": "%s", "practitionerId": "%s", "reasonId": "%s", "startsAt": "%sT%s"}
                                """.formatted(patients.newPatient(), practitioner.id(), practitioner.consulta(), day, time)))
                .andExpect(status().isCreated())
                .andReturn();
        return json.readTree(result.getResponse().getContentAsString()).get("id").asText();
    }

    @Test
    void severalDaysOffGoBackToThePractitionerSayingWhatWasCancelled() throws Exception {
        CreatedPractitioner arias = practitioners.workingEveryDay();
        book(arias, DAY, "10:00:00");
        book(arias, "2026-03-11", "15:00:00");

        mockMvc.perform(post("/time-off").with(csrf())
                        .param("practitionerId", arias.id().toString())
                        .param("date", DAY)
                        .param("untilDate", "2026-03-11")
                        .param("startsAt", "")
                        .param("endsAt", ""))
                .andExpect(redirectedUrl("/practitioners/" + arias.id()))
                .andExpect(flash().attribute("message", containsString("Turnos cancelados (2)")));

        mockMvc.perform(get("/practitioners/" + arias.id()))
                .andExpect(content().string(containsString("del mar 10/03/2026 al mié 11/03/2026")));
    }

    @Test
    void aPatientAlreadyThereIsReportedForReceptionToHandle() throws Exception {
        CreatedPractitioner arias = practitioners.workingEveryDay();
        String arrived = book(arias, DAY, "11:00:00");
        new ArrivalFixture(mockMvc, jdbc).arrived(arrived);

        mockMvc.perform(post("/time-off").with(csrf())
                        .param("practitionerId", arias.id().toString())
                        .param("date", DAY)
                        .param("startsAt", "10:30")
                        .param("endsAt", "13:00"))
                .andExpect(flash().attribute("message", "Ausencia registrada. No tocaba ningún turno."))
                .andExpect(flash().attribute("error", containsString("ya llegaron")));

        mockMvc.perform(get("/practitioners/" + arias.id()))
                .andExpect(content().string(containsString("mar 10/03/2026 de 10:30 a 13:00")));
    }

    @Test
    void aDayAlreadyGoneIsRefusedInSpanish() throws Exception {
        CreatedPractitioner arias = practitioners.workingEveryDay();

        mockMvc.perform(post("/time-off").with(csrf())
                        .param("practitionerId", arias.id().toString())
                        .param("date", "2026-03-09"))
                .andExpect(redirectedUrl("/practitioners/" + arias.id()))
                .andExpect(flash().attribute("error", "No se registran ausencias en días que ya pasaron."));
    }

    @Test
    void timesOverSeveralDaysAreRefusedInSpanish() throws Exception {
        CreatedPractitioner arias = practitioners.workingEveryDay();

        mockMvc.perform(post("/time-off").with(csrf())
                        .param("practitionerId", arias.id().toString())
                        .param("date", DAY)
                        .param("untilDate", "2026-03-12")
                        .param("startsAt", "10:00")
                        .param("endsAt", "12:00"))
                .andExpect(flash().attribute("error", containsString("Revisá la ausencia")));
    }

    @Test
    void anUnknownPractitionerLeadsBackToTheList() throws Exception {
        mockMvc.perform(post("/time-off").with(csrf())
                        .param("practitionerId", UUID.randomUUID().toString())
                        .param("date", DAY))
                .andExpect(redirectedUrl("/practitioners"))
                .andExpect(flash().attribute("error", "No se encontró el profesional."));
    }
}
