package com.clinic.schedule.appointment.web;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.clinic.schedule.appointment.PatientFixture;
import com.clinic.schedule.appointment.PractitionerFixture;
import com.clinic.schedule.appointment.PractitionerFixture.CreatedPractitioner;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Reception's agenda screens over the real stack. They are thin (D-02), so what is worth
 * checking is that they reach the use cases and say the right thing in Spanish.
 */
@SpringBootTest
@AutoConfigureMockMvc
@WithMockUser(roles = "RECEPTION")
class AgendaViewTest {

    /** A Tuesday. */
    private static final String DAY = "2026-03-10";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper json;

    private PractitionerFixture practitioners;
    private PatientFixture patients;

    @BeforeEach
    void setUp() {
        practitioners = new PractitionerFixture(mockMvc, json);
        patients = new PatientFixture(mockMvc, json);
    }

    private String bookThroughTheApi(CreatedPractitioner practitioner, UUID patient, String time) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/appointments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"patientId": "%s", "practitionerId": "%s", "reasonId": "%s", "startsAt": "%sT%s"}
                                """.formatted(patient, practitioner.id(), practitioner.consulta(), DAY, time)))
                .andExpect(status().isCreated())
                .andReturn();
        return json.readTree(result.getResponse().getContentAsString()).get("id").asText();
    }

    private String registerPatientNamed(String name) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/patients")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fullName": "%s"}
                                """.formatted(name)))
                .andExpect(status().isCreated())
                .andReturn();
        return json.readTree(result.getResponse().getContentAsString()).get("id").asText();
    }

    @Test
    void theRootLeadsToTheAgenda() throws Exception {
        mockMvc.perform(get("/")).andExpect(redirectedUrl("/agenda"));
    }

    @Test
    void theAgendaShowsTheDayWithThePatientsName() throws Exception {
        CreatedPractitioner arias = practitioners.workingEveryDay();
        String name = "Ana Agenda " + UUID.randomUUID().toString().substring(0, 6);
        bookThroughTheApi(arias, UUID.fromString(registerPatientNamed(name)), "10:00:00");

        mockMvc.perform(get("/agenda").param("practitionerId", arias.id().toString()).param("date", DAY))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(name)))
                .andExpect(content().string(containsString("10:00 – 10:30")))
                .andExpect(content().string(containsString("Reservado")));
    }

    @Test
    void checkingInFromTheAgendaMarksTheArrival() throws Exception {
        CreatedPractitioner arias = practitioners.workingEveryDay();
        String id = bookThroughTheApi(arias, patients.newPatient(), "10:00:00");

        mockMvc.perform(post("/agenda/appointments/" + id + "/check-in").with(csrf())
                        .param("practitionerId", arias.id().toString()).param("date", DAY))
                .andExpect(redirectedUrlPattern("/agenda?*"))
                .andExpect(flash().attribute("message", "Llegada confirmada."));

        mockMvc.perform(get("/agenda").param("practitionerId", arias.id().toString()).param("date", DAY))
                .andExpect(content().string(containsString("Llegó a las")));
    }

    @Test
    void aRefusalComesBackAsAMessageInSpanish() throws Exception {
        CreatedPractitioner arias = practitioners.workingEveryDay();
        String id = bookThroughTheApi(arias, patients.newPatient(), "10:00:00");
        mockMvc.perform(post("/agenda/appointments/" + id + "/cancel").with(csrf())
                .param("practitionerId", arias.id().toString()).param("date", DAY));

        mockMvc.perform(post("/agenda/appointments/" + id + "/cancel").with(csrf())
                        .param("practitionerId", arias.id().toString()).param("date", DAY))
                .andExpect(flash().attribute("error", containsString("ya fue cancelado")));
    }

    @Test
    void theBookingScreenOffersFreeTimesOnceAReasonIsChosen() throws Exception {
        CreatedPractitioner arias = practitioners.workingTuesdaysWithALunchBreak();
        bookThroughTheApi(arias, patients.newPatient(), "09:00:00");

        mockMvc.perform(get("/agenda/new")
                        .param("practitionerId", arias.id().toString())
                        .param("date", DAY)
                        .param("reasonId", arias.consulta().toString()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("value=\"09:30\"")))
                .andExpect(content().string(not(containsString("value=\"09:15\""))))
                .andExpect(content().string(not(containsString("value=\"13:00\""))));
    }

    @Test
    void bookingFromTheScreenReservesTheAppointment() throws Exception {
        CreatedPractitioner arias = practitioners.workingEveryDay();
        UUID patient = patients.newPatient();

        mockMvc.perform(post("/agenda/new").with(csrf())
                        .param("practitionerId", arias.id().toString())
                        .param("date", DAY)
                        .param("reasonId", arias.consulta().toString())
                        .param("patientId", patient.toString())
                        .param("time", "11:00"))
                .andExpect(redirectedUrlPattern("/agenda?*"))
                .andExpect(flash().attribute("message", "Turno reservado."));

        mockMvc.perform(get("/api/appointments").param("practitionerId", arias.id().toString()).param("date", DAY))
                .andExpect(content().string(containsString(DAY + "T11:00:00")));
    }

    @Test
    void bookingOverATakenSlotStaysOnTheScreenWithTheReason() throws Exception {
        CreatedPractitioner arias = practitioners.workingEveryDay();
        bookThroughTheApi(arias, patients.newPatient(), "11:00:00");

        mockMvc.perform(post("/agenda/new").with(csrf())
                        .param("practitionerId", arias.id().toString())
                        .param("date", DAY)
                        .param("reasonId", arias.consulta().toString())
                        .param("patientId", patients.newPatient().toString())
                        .param("otherTime", "11:15"))
                .andExpect(redirectedUrlPattern("/agenda/new?*"))
                .andExpect(flash().attribute("error", "El profesional ya tiene un turno en ese horario."));
    }

    @Test
    void bookingWithoutChoosingAPatientAsksForIt() throws Exception {
        CreatedPractitioner arias = practitioners.workingEveryDay();

        mockMvc.perform(post("/agenda/new").with(csrf())
                        .param("practitionerId", arias.id().toString())
                        .param("date", DAY)
                        .param("reasonId", arias.consulta().toString())
                        .param("time", "11:00"))
                .andExpect(flash().attribute("error", "Elegí un paciente y un horario."));
    }

    @Test
    void movingFromTheScreenKeepsTheDuration() throws Exception {
        CreatedPractitioner arias = practitioners.workingEveryDay();
        String id = bookThroughTheApi(arias, patients.newPatient(), "10:00:00");

        mockMvc.perform(get("/agenda/appointments/" + id + "/reschedule"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("value=\"10:15\"")));

        mockMvc.perform(post("/agenda/appointments/" + id + "/reschedule").with(csrf())
                        .param("practitionerId", arias.id().toString())
                        .param("date", DAY)
                        .param("time", "15:00"))
                .andExpect(flash().attribute("message", "Turno movido."));

        mockMvc.perform(get("/agenda").param("practitionerId", arias.id().toString()).param("date", DAY))
                .andExpect(content().string(containsString("15:00 – 15:30")));
    }
}
