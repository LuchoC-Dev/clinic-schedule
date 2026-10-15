package com.clinic.schedule.appointment.adapter.in.http;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
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
import org.springframework.jdbc.core.JdbcTemplate;

import com.clinic.schedule.appointment.PatientFixture;
import com.clinic.schedule.appointment.PractitionerFixture.CreatedPractitioner;
import com.clinic.schedule.appointment.PractitionerFixture;
import com.clinic.schedule.arrival.ArrivalFixture;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * A practitioner's absence over the real stack: recorded in the practitioner module, cascaded
 * over the appointments, and refused for later bookings — all in one request.
 */
@SpringBootTest
@AutoConfigureMockMvc
@WithMockUser(roles = "RECEPTION")
class TimeOffApiIT {

    /** A Tuesday. */
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

    private String book(CreatedPractitioner practitioner, String time) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/appointments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"patientId": "%s", "practitionerId": "%s", "reasonId": "%s", "startsAt": "%sT%s"}
                                """.formatted(patients.newPatient(), practitioner.id(), practitioner.consulta(), DAY, time)))
                .andExpect(status().isCreated())
                .andReturn();
        return json.readTree(result.getResponse().getContentAsString()).get("id").asText();
    }

    private JsonNode recordTimeOff(String body) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/time-off").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andReturn();
        return json.readTree(result.getResponse().getContentAsString());
    }

    @Test
    void aWholeDayOffCancelsTheDayAndReportsWhoAlreadyArrived() throws Exception {
        CreatedPractitioner arias = practitioners.workingEveryDay();
        String arrived = book(arias, "09:00:00");
        String notYet = book(arias, "11:00:00");
        new ArrivalFixture(mockMvc, jdbc).arrived(arrived);

        JsonNode result = recordTimeOff("""
                {"practitionerId": "%s", "date": "%s"}
                """.formatted(arias.id(), DAY));

        assertThat(result.get("startsAt").asText()).isEqualTo(DAY + "T00:00:00");
        assertThat(result.get("cancelled")).hasSize(1);
        assertThat(result.get("cancelled").get(0).get("id").asText()).isEqualTo(notYet);
        assertThat(result.get("needsManualHandling")).hasSize(1);
        assertThat(result.get("needsManualHandling").get(0).get("id").asText())
                .isEqualTo(arrived);
    }

    /** The screen says whom it cancelled and who arrived already, by name (D-108). */
    @Test
    void theAnswerNamesThePatientsOfBoth() throws Exception {
        CreatedPractitioner arias = practitioners.workingEveryDay();
        String arrived = book(arias, "09:00:00");
        book(arias, "11:00:00");
        new ArrivalFixture(mockMvc, jdbc).arrived(arrived);

        JsonNode result = recordTimeOff("""
                {"practitionerId": "%s", "date": "%s"}
                """.formatted(arias.id(), DAY));

        String cancelledPatient = result.get("cancelled").get(0).get("patientId").asText();
        String arrivedPatient = result.get("needsManualHandling").get(0).get("patientId").asText();
        assertThat(result.get("patients").get(cancelledPatient).asText()).startsWith("Paciente ");
        assertThat(result.get("patients").get(arrivedPatient).asText()).startsWith("Paciente ");
        assertThat(result.get("patients").get(cancelledPatient).asText())
                .isNotEqualTo(result.get("patients").get(arrivedPatient).asText());
        assertThat(result.get("patients").size()).isEqualTo(2);
    }

    @Test
    void partOfADayOffOnlyCancelsWhatItTouches() throws Exception {
        CreatedPractitioner arias = practitioners.workingEveryDay();
        book(arias, "10:00:00");
        String inside = book(arias, "11:30:00");
        book(arias, "14:00:00");

        JsonNode result = recordTimeOff("""
                {"practitionerId": "%s", "date": "%s", "startsAt": "11:00", "endsAt": "13:00"}
                """.formatted(arias.id(), DAY));

        assertThat(result.get("cancelled")).hasSize(1);
        assertThat(result.get("cancelled").get(0).get("id").asText()).isEqualTo(inside);

        mockMvc.perform(get("/api/appointments").param("practitionerId", arias.id().toString()).param("date", DAY))
                .andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    void bookingDuringTheTimeOffIsRefusedAfterwards() throws Exception {
        CreatedPractitioner arias = practitioners.workingEveryDay();
        recordTimeOff("""
                {"practitionerId": "%s", "date": "%s", "startsAt": "11:00", "endsAt": "13:00"}
                """.formatted(arias.id(), DAY));

        mockMvc.perform(post("/api/appointments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"patientId": "%s", "practitionerId": "%s", "reasonId": "%s", "startsAt": "%sT12:00:00"}
                                """.formatted(patients.newPatient(), arias.id(), arias.consulta(), DAY)))
                .andExpect(status().isConflict());

        // Right after the absence is free again.
        book(arias, "13:00:00");
    }

    @Test
    void theAbsenceShowsInThePractitionersDetail() throws Exception {
        CreatedPractitioner arias = practitioners.workingEveryDay();
        recordTimeOff("""
                {"practitionerId": "%s", "date": "%s", "startsAt": "11:00", "endsAt": "13:00"}
                """.formatted(arias.id(), DAY));

        mockMvc.perform(get("/api/practitioners/" + arias.id()))
                .andExpect(jsonPath("$.timeOff.length()").value(1))
                .andExpect(jsonPath("$.timeOff[0].startsAt").value(DAY + "T11:00:00"))
                .andExpect(jsonPath("$.timeOff[0].endsAt").value(DAY + "T13:00:00"));
    }

    @Test
    void anUnknownPractitionerAnswers400() throws Exception {
        mockMvc.perform(post("/api/time-off")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"practitionerId": "%s", "date": "%s"}
                                """.formatted(UUID.randomUUID(), DAY)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void onlyOneOfTheTwoTimesAnswers400() throws Exception {
        CreatedPractitioner arias = practitioners.workingEveryDay();

        mockMvc.perform(post("/api/time-off")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"practitionerId": "%s", "date": "%s", "startsAt": "11:00"}
                                """.formatted(arias.id(), DAY)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void anAbsenceEndingBeforeItStartsAnswers400() throws Exception {
        CreatedPractitioner arias = practitioners.workingEveryDay();

        mockMvc.perform(post("/api/time-off")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"practitionerId": "%s", "date": "%s", "startsAt": "13:00", "endsAt": "11:00"}
                                """.formatted(arias.id(), DAY)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void anAbsenceOnADayAlreadyGoneAnswers400() throws Exception {
        CreatedPractitioner arias = practitioners.workingEveryDay();

        mockMvc.perform(post("/api/time-off").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"practitionerId": "%s", "date": "2026-03-09"}
                                """.formatted(arias.id())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("day already gone")));
    }

    // --- several days (D-46) -----------------------------------------------------------

    @Test
    void anAbsenceOfSeveralDaysCancelsEachOfThemAndNothingAfter() throws Exception {
        CreatedPractitioner arias = practitioners.workingEveryDay();
        String today = book(arias, "10:00:00");
        String dayAfterTomorrow = bookOn(arias, "2026-03-12", "10:00:00");
        String later = bookOn(arias, "2026-03-13", "10:00:00");

        JsonNode result = recordTimeOff("""
                {"practitionerId": "%s", "date": "%s", "untilDate": "2026-03-12"}
                """.formatted(arias.id(), DAY));

        assertThat(result.get("endsAt").asText()).isEqualTo("2026-03-13T00:00:00");
        assertThat(result.get("cancelled").findValuesAsText("id"))
                .containsExactlyInAnyOrder(today, dayAfterTomorrow);
        assertThat(result.get("cancelled").findValuesAsText("id")).doesNotContain(later);
    }

    @Test
    void timesOverSeveralDaysAnswer400() throws Exception {
        CreatedPractitioner arias = practitioners.workingEveryDay();

        mockMvc.perform(post("/api/time-off").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"practitionerId": "%s", "date": "%s", "untilDate": "2026-03-12",
                                 "startsAt": "10:00", "endsAt": "12:00"}
                                """.formatted(arias.id(), DAY)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("whole days")));
    }

    private String bookOn(CreatedPractitioner practitioner, String day, String time) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/appointments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"patientId": "%s", "practitionerId": "%s", "reasonId": "%s", "startsAt": "%sT%s"}
                                """.formatted(patients.newPatient(), practitioner.id(), practitioner.consulta(), day, time)))
                .andExpect(status().isCreated())
                .andReturn();
        return json.readTree(result.getResponse().getContentAsString()).get("id").asText();
    }
}
