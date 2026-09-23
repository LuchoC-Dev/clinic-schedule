package com.clinic.schedule.appointment.web;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
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

import com.clinic.schedule.appointment.PatientFixture;
import com.clinic.schedule.appointment.PractitionerFixture;
import com.clinic.schedule.appointment.PractitionerFixture.CreatedPractitioner;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * The API over the real stack: Spring, the database, the schema, and the practitioner
 * module answering for reasons and hours.
 *
 * <p>Each test creates its own practitioner and invents its own patients, so tests cannot
 * collide over a slot and nothing has to be cleaned up in between.
 */
@SpringBootTest
@AutoConfigureMockMvc
@WithMockUser(roles = "RECEPTION")
class AppointmentApiTest {

    /** A Tuesday. */
    private static final String DAY = "2026-03-10";
    private static final String TEN = DAY + "T10:00:00";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper json;

    private PractitionerFixture fixture;
    private PatientFixture patients;

    @BeforeEach
    void setUp() {
        fixture = new PractitionerFixture(mockMvc, json);
        patients = new PatientFixture(mockMvc, json);
    }

    /** A booking for a newly registered patient, so no two bookings share a patient. */
    private String bookingBody(UUID practitionerId, UUID reasonId, String startsAt) throws Exception {
        return bookingBody(patients.newPatient(), practitionerId, reasonId, startsAt);
    }

    private String bookingBody(UUID patientId, UUID practitionerId, UUID reasonId, String startsAt) {
        return """
                {"patientId": "%s", "practitionerId": "%s", "reasonId": "%s", "startsAt": "%s"}
                """.formatted(patientId, practitionerId, reasonId, startsAt);
    }

    private JsonNode bookSuccessfully(CreatedPractitioner practitioner, String startsAt) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/appointments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bookingBody(practitioner.id(), practitioner.consulta(), startsAt)))
                .andExpect(status().isCreated())
                .andReturn();
        return json.readTree(result.getResponse().getContentAsString());
    }

    @Test
    void bookingAnswers201WithTheAppointmentAndItsLocation() throws Exception {
        CreatedPractitioner arias = fixture.workingEveryDay();

        mockMvc.perform(post("/api/appointments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bookingBody(arias.id(), arias.consulta(), TEN)))
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.practitionerId").value(arias.id().toString()))
                .andExpect(jsonPath("$.startsAt").value(TEN))
                .andExpect(jsonPath("$.endsAt").value(DAY + "T10:30:00"))
                .andExpect(jsonPath("$.durationMinutes").value(30))
                .andExpect(jsonPath("$.reason").value("consulta"))
                .andExpect(jsonPath("$.status").value("BOOKED"))
                // An absent note comes back as an explicit null rather than being left out,
                // so a client knows the field exists. Asserted with nullValue() and not with
                // doesNotExist(), which would also pass here because it treats null as absent.
                .andExpect(jsonPath("$.note").value(nullValue()));
    }

    @Test
    void theDurationFollowsTheReason() throws Exception {
        CreatedPractitioner arias = fixture.workingEveryDay();

        mockMvc.perform(post("/api/appointments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bookingBody(arias.id(), arias.ecografia(), TEN)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.durationMinutes").value(45))
                .andExpect(jsonPath("$.endsAt").value(DAY + "T10:45:00"))
                .andExpect(jsonPath("$.reason").value("ecografia"));
    }

    @Test
    void anOverlapAnswers409() throws Exception {
        CreatedPractitioner arias = fixture.workingEveryDay();
        bookSuccessfully(arias, TEN);

        mockMvc.perform(post("/api/appointments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bookingBody(arias.id(), arias.consulta(), DAY + "T10:15:00")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.error").value("Conflict"));
    }

    @Test
    void aSlotAcrossTheLunchBreakAnswers409() throws Exception {
        CreatedPractitioner arias = fixture.workingTuesdaysWithALunchBreak();

        mockMvc.perform(post("/api/appointments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bookingBody(arias.id(), arias.consulta(), DAY + "T12:45:00")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("does not work")));
    }

    @Test
    void theLastSlotBeforeTheBreakIsAccepted() throws Exception {
        CreatedPractitioner arias = fixture.workingTuesdaysWithALunchBreak();

        bookSuccessfully(arias, DAY + "T12:30:00");
    }

    @Test
    void anUnregisteredPatientAnswers400() throws Exception {
        CreatedPractitioner arias = fixture.workingEveryDay();

        mockMvc.perform(post("/api/appointments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bookingBody(UUID.randomUUID(), arias.id(), arias.consulta(), TEN)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(
                        org.hamcrest.Matchers.containsString("no registered patient")));
    }

    @Test
    void aReasonThePractitionerDoesNotHaveAnswers400() throws Exception {
        CreatedPractitioner arias = fixture.workingEveryDay();
        CreatedPractitioner benitez = fixture.workingEveryDay();

        mockMvc.perform(post("/api/appointments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bookingBody(arias.id(), benitez.consulta(), TEN)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    void aMissingReasonAnswers400AndNamesIt() throws Exception {
        String withoutReason = """
                {"patientId": "%s", "practitionerId": "%s", "startsAt": "%s"}
                """.formatted(UUID.randomUUID(), UUID.randomUUID(), TEN);

        mockMvc.perform(post("/api/appointments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(withoutReason))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields.reasonId").value("the reason is required"));
    }

    @Test
    void sendingTheSameIdTwiceBooksOnce() throws Exception {
        CreatedPractitioner arias = fixture.workingEveryDay();
        UUID id = UUID.randomUUID();
        String body = """
                {"id": "%s", "patientId": "%s", "practitionerId": "%s", "reasonId": "%s", "startsAt": "%s"}
                """.formatted(id, patients.newPatient(), arias.id(), arias.consulta(), TEN);

        mockMvc.perform(post("/api/appointments").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(id.toString()));
        mockMvc.perform(post("/api/appointments").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(id.toString()));
    }

    @Test
    void reschedulingMovesTheAppointmentAndKeepsItsDuration() throws Exception {
        JsonNode booked = bookSuccessfully(fixture.workingEveryDay(), TEN);

        mockMvc.perform(post("/api/appointments/" + booked.get("id").asText() + "/reschedule")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"startsAt": "2026-03-10T16:00:00"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.startsAt").value("2026-03-10T16:00:00"))
                .andExpect(jsonPath("$.durationMinutes").value(30))
                .andExpect(jsonPath("$.status").value("BOOKED"));
    }

    @Test
    void reschedulingOutsideTheWorkingHoursAnswers409() throws Exception {
        JsonNode booked = bookSuccessfully(fixture.workingTuesdaysWithALunchBreak(), TEN);

        mockMvc.perform(post("/api/appointments/" + booked.get("id").asText() + "/reschedule")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"startsAt": "2026-03-10T19:00:00"}
                                """))
                .andExpect(status().isConflict());
    }

    @Test
    void cancellingKeepsTheAppointmentAsCancelled() throws Exception {
        JsonNode booked = bookSuccessfully(fixture.workingEveryDay(), TEN);

        mockMvc.perform(post("/api/appointments/" + booked.get("id").asText() + "/cancel"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));
    }

    @Test
    void cancellingTwiceAnswers409() throws Exception {
        JsonNode booked = bookSuccessfully(fixture.workingEveryDay(), TEN);
        String cancelUrl = "/api/appointments/" + booked.get("id").asText() + "/cancel";

        mockMvc.perform(post(cancelUrl)).andExpect(status().isOk());
        mockMvc.perform(post(cancelUrl)).andExpect(status().isConflict());
    }

    @Test
    void checkingInRecordsTheArrival() throws Exception {
        JsonNode booked = bookSuccessfully(fixture.workingEveryDay(), TEN);

        mockMvc.perform(post("/api/appointments/" + booked.get("id").asText() + "/check-in"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.checkedInAt").exists())
                .andExpect(jsonPath("$.status").value("BOOKED"));
    }

    @Test
    void checkingInTwiceAnswers409() throws Exception {
        JsonNode booked = bookSuccessfully(fixture.workingEveryDay(), TEN);
        String url = "/api/appointments/" + booked.get("id").asText() + "/check-in";

        mockMvc.perform(post(url)).andExpect(status().isOk());
        mockMvc.perform(post(url)).andExpect(status().isConflict());
    }

    @Test
    void aNewAppointmentHasNoCheckInAndNoNotice() throws Exception {
        JsonNode booked = bookSuccessfully(fixture.workingEveryDay(), TEN);

        org.assertj.core.api.Assertions.assertThat(booked.get("checkedInAt").isNull()).isTrue();
        org.assertj.core.api.Assertions.assertThat(booked.get("autoCancellationSuspended").asBoolean()).isFalse();
    }

    @Test
    void suspendingTheAutomaticCancellationIsRecorded() throws Exception {
        JsonNode booked = bookSuccessfully(fixture.workingEveryDay(), TEN);

        mockMvc.perform(post("/api/appointments/" + booked.get("id").asText() + "/suspend-auto-cancellation"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.autoCancellationSuspended").value(true));
    }

    @Test
    void suspendingAfterTheCheckInAnswers409() throws Exception {
        JsonNode booked = bookSuccessfully(fixture.workingEveryDay(), TEN);
        String id = booked.get("id").asText();
        mockMvc.perform(post("/api/appointments/" + id + "/check-in")).andExpect(status().isOk());

        mockMvc.perform(post("/api/appointments/" + id + "/suspend-auto-cancellation"))
                .andExpect(status().isConflict());
    }

    @Test
    void reschedulingAfterTheCheckInAnswers409() throws Exception {
        JsonNode booked = bookSuccessfully(fixture.workingEveryDay(), TEN);
        String id = booked.get("id").asText();
        mockMvc.perform(post("/api/appointments/" + id + "/check-in")).andExpect(status().isOk());

        mockMvc.perform(post("/api/appointments/" + id + "/reschedule")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"startsAt": "2026-03-10T16:00:00"}
                                """))
                .andExpect(status().isConflict());
    }

    @Test
    void anUnknownAppointmentAnswers404() throws Exception {
        mockMvc.perform(post("/api/appointments/" + UUID.randomUUID() + "/cancel"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.error").value("Not found"));
    }

    @Test
    void theDayAgendaComesBackInOrderAndWithoutCancelledOnes() throws Exception {
        CreatedPractitioner arias = fixture.workingEveryDay();
        bookSuccessfully(arias, DAY + "T15:00:00");
        bookSuccessfully(arias, DAY + "T09:00:00");
        JsonNode toCancel = bookSuccessfully(arias, DAY + "T12:00:00");
        mockMvc.perform(post("/api/appointments/" + toCancel.get("id").asText() + "/cancel"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/appointments")
                        .param("practitionerId", arias.id().toString())
                        .param("date", DAY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].startsAt").value(DAY + "T09:00:00"))
                .andExpect(jsonPath("$[1].startsAt").value(DAY + "T15:00:00"));
    }

    @Test
    void theDayAgendaOfAPractitionerWithNothingBookedIsEmpty() throws Exception {
        mockMvc.perform(get("/api/appointments")
                        .param("practitionerId", UUID.randomUUID().toString())
                        .param("date", DAY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    // --- protections (D-38) -------------------------------------------------------------

    @Test
    void bookingATimeAlreadyGoneAnswers409() throws Exception {
        CreatedPractitioner arias = fixture.workingEveryDay();

        mockMvc.perform(post("/api/appointments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bookingBody(arias.id(), arias.consulta(), "2026-03-09T10:00:00")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(containsString("in the past")));
    }

    @Test
    void bookingMoreThanAYearAheadAnswers409() throws Exception {
        CreatedPractitioner arias = fixture.workingEveryDay();

        mockMvc.perform(post("/api/appointments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bookingBody(arias.id(), arias.consulta(), "2027-03-11T10:00:00")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(containsString("more than a year ahead")));
    }

    @Test
    void aNoteLongerThanItsColumnAnswers400() throws Exception {
        CreatedPractitioner arias = fixture.workingEveryDay();

        mockMvc.perform(post("/api/appointments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"patientId": "%s", "practitionerId": "%s", "reasonId": "%s", "startsAt": "%s",
                                 "note": "%s"}
                                """.formatted(patients.newPatient(), arias.id(), arias.consulta(), TEN, "x".repeat(501))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields.note").exists());
    }

    @Test
    void checkingInOnAnotherDayAnswers409() throws Exception {
        CreatedPractitioner arias = fixture.workingEveryDay();
        String id = bookSuccessfully(arias, "2026-03-12T10:00:00").get("id").asText();

        mockMvc.perform(post("/api/appointments/" + id + "/check-in"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(containsString("on its own day")));
    }

    @Test
    void aMalformedIdAnswers400WithoutTheInnerWorkings() throws Exception {
        mockMvc.perform(post("/api/appointments/not-an-id/cancel"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Parameter 'id' has an invalid value"));
    }

    @Test
    void aMissingParameterAnswers400InTheUsualBody() throws Exception {
        mockMvc.perform(get("/api/appointments").param("date", DAY))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Parameter 'practitionerId' is required"));
    }

    @Test
    void reschedulingToATimeAlreadyGoneAnswers409() throws Exception {
        CreatedPractitioner arias = fixture.workingEveryDay();
        String id = bookSuccessfully(arias, TEN).get("id").asText();

        mockMvc.perform(post("/api/appointments/" + id + "/reschedule")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"startsAt": "2026-03-09T10:00:00"}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(containsString("in the past")));
    }

    @Test
    void aLateArrivalNoticeOnAnotherDayAnswers409() throws Exception {
        CreatedPractitioner arias = fixture.workingEveryDay();
        String id = bookSuccessfully(arias, "2026-03-12T10:00:00").get("id").asText();

        mockMvc.perform(post("/api/appointments/" + id + "/suspend-auto-cancellation"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(containsString("on its own day")));
    }
}
