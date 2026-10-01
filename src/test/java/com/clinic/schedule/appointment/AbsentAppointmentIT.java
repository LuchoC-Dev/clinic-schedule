package com.clinic.schedule.appointment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.sql.Timestamp;
import java.time.LocalDateTime;
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

import com.clinic.schedule.appointment.PractitionerFixture.CreatedPractitioner;
import com.clinic.schedule.appointment.application.usecase.MarkAbsentAppointmentsUseCase;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * An appointment whose patient never came is absent, not cancelled, over the real stack (D-61):
 * the sweep marks it, the API and the agenda show it, and nothing more happens to it. "Now" is
 * 10 March 2026 at midnight, so appointments are booked for today and moved back to the 9th in the
 * table, since nobody may book in the past.
 */
@SpringBootTest
@AutoConfigureMockMvc
@WithMockUser(roles = "RECEPTION")
class AbsentAppointmentIT {

    private static final LocalDateTime YESTERDAY_EVENING = LocalDateTime.of(2026, 3, 9, 20, 0);

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper json;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private MarkAbsentAppointmentsUseCase markAbsent;

    private CreatedPractitioner arias;
    private PatientFixture patients;

    @BeforeEach
    void setUp() throws Exception {
        arias = new PractitionerFixture(mockMvc, json).workingEveryDay();
        patients = new PatientFixture(mockMvc, json);
    }

    /** Booked today at ten, then moved back to the evening before, as if its time had passed. */
    private String bookedYesterdayEvening() throws Exception {
        String id = json.readTree(mockMvc.perform(post("/api/appointments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"patientId": "%s", "practitionerId": "%s", "reasonId": "%s", "startsAt": "2026-03-10T10:00:00"}
                                """.formatted(patients.newPatient(), arias.id(), arias.consulta())))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString()).get("id").asText();
        jdbc.update("update appointment set starts_at = ? where id = ?", Timestamp.valueOf(YESTERDAY_EVENING),
                UUID.fromString(id));
        return id;
    }

    private String statusOf(String id) {
        return jdbc.queryForObject("select status from appointment where id = ?", String.class, UUID.fromString(id));
    }

    @Test
    void aPatientWhoNeverCameIsAbsent() throws Exception {
        String absent = bookedYesterdayEvening();
        String arrived = bookedYesterdayEvening();
        // Arrived before the queues took the arrival over: the check-in they kept (D-65).
        jdbc.update("insert into appointment_check_in (appointment_id, checked_in_at) values (?, ?)",
                UUID.fromString(arrived), Timestamp.valueOf(YESTERDAY_EVENING.plusMinutes(5)));

        assertThat(markAbsent.handle()).extracting(appointment -> appointment.id().value().toString())
                .contains(absent)
                .doesNotContain(arrived);

        assertThat(statusOf(absent)).isEqualTo("ABSENT");
        assertThat(statusOf(arrived)).isEqualTo("BOOKED");
    }

    @Test
    void aPatientWhoWarnedIsAbsentOnceTheirDayIsOver() throws Exception {
        String warned = bookedYesterdayEvening();
        jdbc.update("update appointment set auto_cancellation_suspended = true where id = ?", UUID.fromString(warned));

        markAbsent.handle();

        assertThat(statusOf(warned)).isEqualTo("ABSENT");
    }

    @Test
    void nothingMoreHappensToAnAbsentAppointment() throws Exception {
        String absent = bookedYesterdayEvening();
        markAbsent.handle();

        for (String operation : new String[] {"cancel", "late-arrival-notice"}) {
            mockMvc.perform(post("/api/appointments/" + absent + "/" + operation)
                            .contentType(MediaType.APPLICATION_JSON).content("{\"requestedBy\":\"PATIENT\"}"))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.message").value(containsString("absent")));
        }
        mockMvc.perform(post("/api/appointments/" + absent + "/reschedule")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"startsAt": "2026-03-11T10:00:00"}
                                """))
                .andExpect(status().isConflict());
        assertThat(statusOf(absent)).isEqualTo("ABSENT");
    }

    @Test
    void theAgendaShowsWhoDidNotComeWithNothingToDoAboutIt() throws Exception {
        String absent = bookedYesterdayEvening();
        markAbsent.handle();

        mockMvc.perform(get("/agenda").param("date", "2026-03-09").param("practitionerId", arias.id().toString()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Ausente")))
                .andExpect(content().string(not(containsString("/agenda/appointments/" + absent + "/"))));
        mockMvc.perform(get("/api/appointments").param("date", "2026-03-09").param("practitionerId", arias.id().toString()))
                .andExpect(jsonPath("$[?(@.id == '" + absent + "')].status").value("ABSENT"));
    }

    @Test
    void anAbsentAppointmentFreesItsTime() throws Exception {
        // Booked for today, then marked absent in the table: its slot is free for somebody else.
        String first = json.readTree(mockMvc.perform(post("/api/appointments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"patientId": "%s", "practitionerId": "%s", "reasonId": "%s", "startsAt": "2026-03-10T11:00:00"}
                                """.formatted(patients.newPatient(), arias.id(), arias.consulta())))
                .andReturn().getResponse().getContentAsString()).get("id").asText();
        jdbc.update("update appointment set status = 'ABSENT' where id = ?", UUID.fromString(first));

        mockMvc.perform(post("/api/appointments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"patientId": "%s", "practitionerId": "%s", "reasonId": "%s", "startsAt": "2026-03-10T11:00:00"}
                                """.formatted(patients.newPatient(), arias.id(), arias.consulta())))
                .andExpect(status().isCreated());
    }
}
