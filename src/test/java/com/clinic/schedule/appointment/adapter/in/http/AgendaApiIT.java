package com.clinic.schedule.appointment.adapter.in.http;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.anonymous;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import com.clinic.schedule.appointment.PatientFixture;
import com.clinic.schedule.appointment.PractitionerFixture;
import com.clinic.schedule.appointment.PractitionerFixture.CreatedPractitioner;
import com.clinic.schedule.arrival.ArrivalFixture;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Reception's agenda of a day for its application (D-98), over the real stack: the appointments not cancelled,
 * in order, with the names of their patients and practitioners, who arrived and who warned. Each test books
 * with practitioners of its own, on the tests' "today", Tuesday 10 March 2026.
 */
@SpringBootTest
@AutoConfigureMockMvc
@WithMockUser(username = "recepcion", roles = "RECEPTION")
class AgendaApiIT {

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

    private String book(CreatedPractitioner practitioner, UUID patient, String time) throws Exception {
        return json.readTree(mockMvc.perform(post("/api/appointments").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"patientId": "%s", "practitionerId": "%s", "reasonId": "%s", "startsAt": "%sT%s:00"}
                                """.formatted(patient, practitioner.id(), practitioner.consulta(), DAY, time)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString()).get("id").asText();
    }

    private UUID patientWithADocument(String fullName, String dni) throws Exception {
        return UUID.fromString(json.readTree(mockMvc.perform(post("/api/patients")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fullName": "%s", "documentType": "DNI", "documentNumber": "%s"}
                                """.formatted(fullName, dni)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString()).get("id").asText());
    }

    private String nameOf(CreatedPractitioner practitioner) {
        return jdbc.queryForObject("select full_name from practitioner where id = ?", String.class, practitioner.id());
    }

    @Test
    void aPractitionersDayComesInOrderWithItsNamesWhoArrivedAndWhoWarned() throws Exception {
        CreatedPractitioner practitioner = practitioners.workingEveryDay();
        String dni = String.valueOf(ThreadLocalRandom.current().nextInt(50_000_000, 99_999_999));
        UUID withDocument = patientWithADocument("Rosa Agenda", dni);
        String late = book(practitioner, patients.newPatient(), "11:00");
        String arrived = book(practitioner, withDocument, "10:00");
        String cancelled = book(practitioner, patients.newPatient(), "12:00");
        new ArrivalFixture(mockMvc, jdbc).arrived(arrived);
        mockMvc.perform(post("/api/appointments/" + late + "/late-arrival-notice")).andExpect(status().isOk());
        mockMvc.perform(post("/api/appointments/" + cancelled + "/cancel").contentType(MediaType.APPLICATION_JSON)
                .content("{\"requestedBy\": \"PATIENT\"}")).andExpect(status().isOk());

        mockMvc.perform(get("/api/agenda").param("practitionerId", practitioner.id().toString()).param("date", DAY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.date").value(DAY))
                .andExpect(jsonPath("$.practitionerId").value(practitioner.id().toString()))
                .andExpect(jsonPath("$.practitioners[*].fullName", hasItem(nameOf(practitioner))))
                .andExpect(jsonPath("$.appointments.length()").value(2))
                .andExpect(jsonPath("$.appointments[0].id").value(arrived))
                .andExpect(jsonPath("$.appointments[0].startsAt").value(DAY + "T10:00:00"))
                .andExpect(jsonPath("$.appointments[0].durationMinutes").value(30))
                .andExpect(jsonPath("$.appointments[0].patient").value("Rosa Agenda"))
                .andExpect(jsonPath("$.appointments[0].patientDocument").value("DNI " + dni))
                .andExpect(jsonPath("$.appointments[0].practitioner").value(nameOf(practitioner)))
                .andExpect(jsonPath("$.appointments[0].reason").value("consulta"))
                .andExpect(jsonPath("$.appointments[0].status").value("BOOKED"))
                .andExpect(jsonPath("$.appointments[0].arrivedAt").isNotEmpty())
                .andExpect(jsonPath("$.appointments[0].bookedVia").value("RECEPTION"))
                .andExpect(jsonPath("$.appointments[1].id").value(late))
                .andExpect(jsonPath("$.appointments[1].patientDocument").isEmpty())
                .andExpect(jsonPath("$.appointments[1].arrivedAt").isEmpty())
                .andExpect(jsonPath("$.appointments[1].lateArrivalNotice").value(true))
                .andExpect(jsonPath("$.appointments[1].movedOnWeb").value(false));
    }

    /** Without a practitioner, everyone's; without a day, today's by the practice's clock, never the browser's. */
    @Test
    void withoutAPractitionerItIsEveryonesAndWithoutADayItIsToday() throws Exception {
        CreatedPractitioner one = practitioners.workingEveryDay();
        CreatedPractitioner other = practitioners.workingEveryDay();
        String first = book(one, patients.newPatient(), "09:00");
        String second = book(other, patients.newPatient(), "09:00");

        mockMvc.perform(get("/api/agenda"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.date").value(DAY))
                .andExpect(jsonPath("$.practitionerId").isEmpty())
                .andExpect(jsonPath("$.appointments[*].id", hasItems(first, second)));

        mockMvc.perform(get("/api/agenda").param("practitionerId", other.id().toString()))
                .andExpect(jsonPath("$.appointments[*].practitionerId", everyItem(is(other.id().toString()))))
                .andExpect(jsonPath("$.appointments[*].id", not(hasItem(first))));
    }

    /** Reception's alone for now (D-34): the practitioners' application reads it when it is built. */
    @Test
    void onlyReceptionReadsIt() throws Exception {
        mockMvc.perform(get("/api/agenda").with(httpBasic("arias", "arias123"))).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/agenda").with(httpBasic("admin", "admin123"))).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/agenda").with(httpBasic("sala", "sala123"))).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/agenda").with(anonymous())).andExpect(status().isUnauthorized());
    }

    @Test
    void aDayThatIsNoneIsA400() throws Exception {
        mockMvc.perform(get("/api/agenda").param("date", "mañana")).andExpect(status().isBadRequest());
    }

    // --- D-100 ------------------------------------------------------------------------------

    /** The week around the day, Monday to Sunday, as the Thymeleaf agenda's. */
    @Test
    void aWeekIsTheDaysWeekFromMondayToSunday() throws Exception {
        CreatedPractitioner practitioner = practitioners.workingEveryDay();
        String tuesday = book(practitioner, patients.newPatient(), "10:00");
        String sunday = bookOn(practitioner, patients.newPatient(), "2026-03-15T10:00:00");
        String nextMonday = bookOn(practitioner, patients.newPatient(), "2026-03-16T10:00:00");

        mockMvc.perform(get("/api/agenda").param("practitionerId", practitioner.id().toString())
                        .param("date", "2026-03-12").param("week", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.date").value("2026-03-12"))
                .andExpect(jsonPath("$.from").value("2026-03-09"))
                .andExpect(jsonPath("$.to").value("2026-03-15"))
                .andExpect(jsonPath("$.appointments[*].id", hasItems(tuesday, sunday)))
                .andExpect(jsonPath("$.appointments[*].id", not(hasItem(nextMonday))));
        mockMvc.perform(get("/api/agenda").param("practitionerId", practitioner.id().toString())
                        .param("date", "2026-03-12"))
                .andExpect(jsonPath("$.from").value("2026-03-12"))
                .andExpect(jsonPath("$.to").value("2026-03-12"))
                .andExpect(jsonPath("$.appointments.length()").value(0));
    }

    /** The times a reason fits, the practitioner's hours and the appointments already there considered. */
    @Test
    void theFreeTimesOfAReasonLeaveOutWhatIsTakenAndADayNotWorkedIsSaidSo() throws Exception {
        CreatedPractitioner practitioner = practitioners.workingTuesdaysWithALunchBreak();
        String booked = book(practitioner, patients.newPatient(), "09:00");

        mockMvc.perform(get("/api/agenda/free-times").param("practitionerId", practitioner.id().toString())
                        .param("reasonId", practitioner.consulta().toString()).param("date", DAY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.practitionerWorks").value(true))
                .andExpect(jsonPath("$.times", not(hasItem("09:00:00"))))
                .andExpect(jsonPath("$.times", not(hasItem("09:15:00"))))
                .andExpect(jsonPath("$.times", hasItems("09:30:00", "12:30:00", "14:00:00", "17:30:00")))
                .andExpect(jsonPath("$.times", not(hasItem("12:45:00"))));
        mockMvc.perform(get("/api/agenda/free-times").param("practitionerId", practitioner.id().toString())
                        .param("reasonId", practitioner.consulta().toString()).param("date", "2026-03-11"))
                .andExpect(jsonPath("$.practitionerWorks").value(false))
                .andExpect(jsonPath("$.times.length()").value(0));
        // Moving it, its own time is not taken.
        mockMvc.perform(get("/api/agenda/free-times").param("appointmentId", booked).param("date", DAY))
                .andExpect(jsonPath("$.times", hasItems("09:00:00", "09:15:00")));
        mockMvc.perform(get("/api/agenda/free-times").param("date", DAY))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("ILLEGAL_ARGUMENT"));
    }

    /** Registered and booked at once (D-75): a refusal of either leaves nothing behind. */
    @Test
    void aNewPatientIsRegisteredAndBookedAtOnceOrNotAtAll() throws Exception {
        CreatedPractitioner practitioner = practitioners.workingTuesdaysWithALunchBreak();
        String dni = String.valueOf(ThreadLocalRandom.current().nextInt(50_000_000, 99_999_999));

        String id = json.readTree(mockMvc.perform(post("/api/appointments/with-new-patient")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(newPatientBooking(practitioner, "Nueva Paciente", dni, DAY + "T10:00:00")))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString()).get("patientId").asText();
        mockMvc.perform(get("/api/patients/" + id))
                .andExpect(jsonPath("$.fullName").value("Nueva Paciente"))
                .andExpect(jsonPath("$.document.number").value(dni))
                .andExpect(jsonPath("$.phone.number").value("1145556666"))
                .andExpect(jsonPath("$.phone.owner.relation").value("MOTHER"))
                .andExpect(jsonPath("$.phone.owner.name").value("Rosa Madre"))
                .andExpect(jsonPath("$.email.address").value("nueva@mail.com"))
                .andExpect(jsonPath("$.email.owner").isEmpty());

        // Someone else's document: a conflict, and nothing booked.
        mockMvc.perform(post("/api/appointments/with-new-patient").contentType(MediaType.APPLICATION_JSON)
                        .content(newPatientBooking(practitioner, "Otra Persona", dni, DAY + "T11:00:00")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DUPLICATE_DOCUMENT"));
        // A time the practitioner does not work: refused, and nobody registered.
        String other = String.valueOf(ThreadLocalRandom.current().nextInt(50_000_000, 99_999_999));
        mockMvc.perform(post("/api/appointments/with-new-patient").contentType(MediaType.APPLICATION_JSON)
                        .content(newPatientBooking(practitioner, "Sin Registrar", other, DAY + "T13:15:00")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("OUTSIDE_WORKING_HOURS"));
        mockMvc.perform(get("/api/patients").param("query", other)).andExpect(jsonPath("$.length()").value(0));
        mockMvc.perform(get("/api/agenda").param("practitionerId", practitioner.id().toString()).param("date", DAY))
                .andExpect(jsonPath("$.appointments.length()").value(1));
    }

    /** Each refusal says its name, for a screen to say it in its own words (D-100). */
    @Test
    void aRefusalSaysItsCode() throws Exception {
        CreatedPractitioner practitioner = practitioners.workingEveryDay();
        book(practitioner, patients.newPatient(), "10:00");

        mockMvc.perform(post("/api/appointments").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"patientId": "%s", "practitionerId": "%s", "reasonId": "%s", "startsAt": "%sT10:15:00"}
                                """.formatted(patients.newPatient(), practitioner.id(), practitioner.consulta(), DAY)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PRACTITIONER_ALREADY_BOOKED"))
                .andExpect(jsonPath("$.message").isNotEmpty());
    }

    /** All of a patient's appointments, the cancelled ones with who cancelled them (D-76). */
    @Test
    void aPatientsAppointmentsIncludeTheCancelledOnes() throws Exception {
        CreatedPractitioner practitioner = practitioners.workingEveryDay();
        UUID patient = patients.newPatient();
        String kept = book(practitioner, patient, "10:00");
        String cancelled = bookOn(practitioner, patient, "2026-03-12T10:00:00");
        mockMvc.perform(post("/api/appointments/" + cancelled + "/cancel").contentType(MediaType.APPLICATION_JSON)
                .content("{\"requestedBy\": \"PATIENT\", \"note\": \"Viaja\"}")).andExpect(status().isOk());

        mockMvc.perform(get("/api/patients/" + patient + "/appointments"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[?(@.id == '%s')].status".formatted(kept)).value(contains("BOOKED")))
                .andExpect(jsonPath("$[?(@.id == '%s')].status".formatted(cancelled)).value(contains("CANCELLED")))
                .andExpect(jsonPath("$[?(@.id == '%s')].cancellation.requestedBy".formatted(cancelled))
                        .value(contains("PATIENT")))
                .andExpect(jsonPath("$[?(@.id == '%s')].cancellation.by".formatted(cancelled)).value(contains("recepcion")))
                .andExpect(jsonPath("$[?(@.id == '%s')].cancellation.note".formatted(cancelled)).value(contains("Viaja")))
                .andExpect(jsonPath("$[*].practitioner", everyItem(is(nameOf(practitioner)))));
        mockMvc.perform(get("/api/patients/" + patient + "/appointments").with(httpBasic("arias", "arias123")))
                .andExpect(status().isForbidden());
    }

    private String bookOn(CreatedPractitioner practitioner, UUID patient, String startsAt) throws Exception {
        return json.readTree(mockMvc.perform(post("/api/appointments").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"patientId": "%s", "practitionerId": "%s", "reasonId": "%s", "startsAt": "%s"}
                                """.formatted(patient, practitioner.id(), practitioner.consulta(), startsAt)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString()).get("id").asText();
    }

    private static String newPatientBooking(CreatedPractitioner practitioner, String name, String dni,
            String startsAt) {
        return """
                {"patient": {"fullName": "%s", "documentType": "DNI", "documentNumber": "%s",
                             "phone": {"value": "1145556666", "owner": {"name": "Rosa Madre", "relation": "MOTHER"}},
                             "email": {"value": "nueva@mail.com", "owner": null}},
                 "practitionerId": "%s", "reasonId": "%s", "startsAt": "%s"}
                """.formatted(name, dni, practitioner.id(), practitioner.consulta(), startsAt);
    }
}
