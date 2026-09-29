package com.clinic.schedule.clinicalrecord.adapter.in.http;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import com.clinic.schedule.appointment.PatientFixture;
import com.clinic.schedule.appointment.PractitionerFixture;
import com.clinic.schedule.appointment.PractitionerFixture.CreatedPractitioner;
import com.clinic.schedule.arrival.ArrivalFixture;
import com.clinic.schedule.clinicalrecord.PractitionerUserFixture;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Consultations and clinical records over the real stack (D-47 to D-51). Reception, the default
 * user here, books and checks patients in; each practitioner acts through a user of their own.
 * The tests' clock is at the start of Tuesday 10 March 2026, so that day is "today".
 */
@SpringBootTest
@AutoConfigureMockMvc
@WithMockUser(roles = "RECEPTION")
class ConsultationApiIT {

    private static final String TODAY = "2026-03-10";
    private static final String TOMORROW = "2026-03-11";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper json;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private UserDetailsService userDetails;

    private PractitionerFixture practitioners;
    private PatientFixture patients;
    private PractitionerUserFixture users;

    private CreatedPractitioner arias;
    private RequestPostProcessor asArias;
    private UUID patient;

    @BeforeEach
    void setUp() throws Exception {
        practitioners = new PractitionerFixture(mockMvc, json);
        patients = new PatientFixture(mockMvc, json);
        users = new PractitionerUserFixture(jdbc, userDetails);
        arias = practitioners.workingEveryDay();
        asArias = users.userFor(arias.id());
        patient = patients.newPatient();
    }

    private String book(CreatedPractitioner practitioner, String day, String time) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/appointments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"patientId": "%s", "practitionerId": "%s", "reasonId": "%s", "startsAt": "%sT%s"}
                                """.formatted(patient, practitioner.id(), practitioner.consulta(), day, time)))
                .andExpect(status().isCreated())
                .andReturn();
        return json.readTree(result.getResponse().getContentAsString()).get("id").asText();
    }

    private void checkIn(String appointmentId) throws Exception {
        new ArrivalFixture(mockMvc, jdbc).arrivedAndCalled(appointmentId, asArias);
    }

    private String arrivedToday() throws Exception {
        String appointment = book(arias, TODAY, "10:00:00");
        checkIn(appointment);
        return appointment;
    }

    private static String consultation(UUID patient, String notes) {
        return """
                {"patientId": "%s", "notes": "%s", "items": [
                  {"kind": "PRESCRIPTION", "description": "enalapril 10 mg"},
                  {"kind": "PRESCRIPTION", "description": "amlodipina 5 mg"}]}
                """.formatted(patient, notes);
    }

    private ResultActions send(RequestPostProcessor as, String body)
            throws Exception {
        return mockMvc.perform(post("/api/consultations").with(as)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    private ResultActions readRecord(RequestPostProcessor as) throws Exception {
        return mockMvc.perform(get("/api/clinical-records/" + patient).with(as));
    }

    @Test
    void aPractitionerRecordsTheConsultationOfAPatientWhoArrived() throws Exception {
        arrivedToday();

        send(asArias, consultation(patient, "Control de hipertensión."))
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.practitionerId").value(arias.id().toString()))
                .andExpect(jsonPath("$.recordedAt").value("2026-03-10T00:00:00"))
                .andExpect(jsonPath("$.items[0].kind").value("PRESCRIPTION"))
                .andExpect(jsonPath("$.items[1].description").value("amlodipina 5 mg"));

        readRecord(asArias)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.consultations", hasSize(1)))
                .andExpect(jsonPath("$.consultations[0].notes").value("Control de hipertensión."))
                .andExpect(jsonPath("$.consultations[0].practitionerName").value("Dra. Arias"))
                .andExpect(jsonPath("$.consultations[0].items", hasSize(2)));
    }

    @Test
    void theRecordCarriesThePatientsDateOfBirthButNotHowToReachThem() throws Exception {
        book(arias, TODAY, "10:00:00");
        readRecord(asArias).andExpect(jsonPath("$.patient.birthDate").isEmpty());

        mockMvc.perform(put("/api/patients/" + patient + "/details")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"birthDate": "1981-03-12", "phone": {"number": "1145556666"}}
                                """))
                .andExpect(status().isOk());

        readRecord(asArias)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.patient.birthDate").value("1981-03-12"))
                .andExpect(jsonPath("$.patient.phone").doesNotExist())
                .andExpect(jsonPath("$.patient.email").doesNotExist());
    }

    @Test
    void aRecordThatMayNotBeReadTellsNothingOfThePatient() throws Exception {
        mockMvc.perform(put("/api/patients/" + patient + "/details")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"birthDate": "1981-03-12"}
                                """))
                .andExpect(status().isOk());

        readRecord(asArias)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.patient").doesNotExist());
    }

    @Test
    void beforeThePatientArrivesTheRecordIsReadButNoConsultationIsRecorded() throws Exception {
        book(arias, TODAY, "10:00:00");

        readRecord(asArias).andExpect(status().isOk()).andExpect(jsonPath("$.consultations", hasSize(0)));
        send(asArias, consultation(patient, "Todavía no llegó."))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403));
    }

    @Test
    void aPatientThePractitionerIsNotSeeingIsForbidden() throws Exception {
        readRecord(asArias).andExpect(status().isForbidden()).andExpect(jsonPath("$.status").value(403));
        send(asArias, consultation(patient, "Sin turno.")).andExpect(status().isForbidden());
    }

    @Test
    void aFutureAppointmentGrantsNothingBeforeItsDay() throws Exception {
        book(arias, TOMORROW, "10:00:00");

        readRecord(asArias).andExpect(status().isForbidden());
    }

    @Test
    void aCancelledAppointmentGrantsNothing() throws Exception {
        String appointment = book(arias, TODAY, "10:00:00");
        mockMvc.perform(post("/api/appointments/" + appointment + "/cancel")).andExpect(status().isOk());

        readRecord(asArias).andExpect(status().isForbidden());
    }

    @Test
    void anAbsentAppointmentTodayStillOpensTheRecordButRecordsNothing() throws Exception {
        String appointment = book(arias, TODAY, "10:00:00");
        // The fixed clock stands at midnight, before any of today's appointments could be absent.
        jdbc.update("update appointment set status = 'ABSENT' where id = ?", UUID.fromString(appointment));

        readRecord(asArias).andExpect(status().isOk());
        send(asArias, consultation(patient, "Control.")).andExpect(status().isForbidden());
    }

    @Test
    void anotherPractitionerReadsTheWholeRecordOnlyWhenSeeingThePatient() throws Exception {
        arrivedToday();
        send(asArias, consultation(patient, "Control de hipertensión.")).andExpect(status().isCreated());
        CreatedPractitioner benitez = practitioners.workingEveryDay();
        RequestPostProcessor asBenitez = users.userFor(benitez.id());

        readRecord(asBenitez).andExpect(status().isForbidden());

        book(benitez, TODAY, "11:00:00");
        readRecord(asBenitez)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.consultations", hasSize(1)))
                .andExpect(jsonPath("$.consultations[0].practitionerId").value(arias.id().toString()));
    }

    @Test
    void afterAConsultationTheRecordStaysOpenWithoutAVisit() throws Exception {
        String appointment = arrivedToday();
        send(asArias, consultation(patient, "Control.")).andExpect(status().isCreated());

        // The appointment cancelled, and the call a day old: no visit today, the consultation alone keeps it open.
        mockMvc.perform(post("/api/appointments/" + appointment + "/cancel")).andExpect(status().isOk());
        new ArrivalFixture(mockMvc, jdbc).seenTheDayBefore(patient);

        readRecord(asArias).andExpect(status().isOk()).andExpect(jsonPath("$.consultations", hasSize(1)));
    }

    @Test
    void twoConsultationsOnTheSameDayAreTwo() throws Exception {
        arrivedToday();
        send(asArias, consultation(patient, "Primera.")).andExpect(status().isCreated());
        send(asArias, consultation(patient, "Segunda.")).andExpect(status().isCreated());

        readRecord(asArias).andExpect(jsonPath("$.consultations", hasSize(2)));
    }

    @Test
    void aRetryWithTheSameIdRecordsOnce() throws Exception {
        arrivedToday();
        String body = """
                {"id": "%s", "patientId": "%s", "notes": "Una sola vez."}
                """.formatted(UUID.randomUUID(), patient);

        send(asArias, body).andExpect(status().isCreated());
        send(asArias, body).andExpect(status().isCreated());

        readRecord(asArias).andExpect(jsonPath("$.consultations", hasSize(1)));
    }

    @Test
    void anUnknownPatientIsForbiddenAsWell() throws Exception {
        mockMvc.perform(get("/api/clinical-records/" + UUID.randomUUID()).with(asArias))
                .andExpect(status().isForbidden());
    }

    @Test
    void receptionCannotReachClinicalRecords() throws Exception {
        arrivedToday();

        mockMvc.perform(get("/api/clinical-records/" + patient)).andExpect(status().isForbidden());
        mockMvc.perform(post("/api/consultations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(consultation(patient, "Recepción no escribe.")))
                .andExpect(status().isForbidden());
    }

    @Test
    void aMalformedConsultationIsA400NamingTheField() throws Exception {
        arrivedToday();

        send(asArias, """
                {"patientId": "%s", "notes": "  "}
                """.formatted(patient))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields.notes").exists());
        send(asArias, """
                {"patientId": "%s", "notes": "Control.", "items": [{"kind": "PRESCRIPTION", "description": ""}]}
                """.formatted(patient))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields['items[0].description']").exists());
        send(asArias, """
                {"patientId": "%s", "notes": "Control.", "items": [{"kind": "XRAY", "description": "tórax"}]}
                """.formatted(patient))
                .andExpect(status().isBadRequest());
    }

    // --- addenda (D-52) -----------------------------------------------------------------

    private String recordedConsultation() throws Exception {
        arrivedToday();
        MvcResult result = send(asArias, consultation(patient, "Control de hipertensión."))
                .andExpect(status().isCreated())
                .andReturn();
        return json.readTree(result.getResponse().getContentAsString()).get("id").asText();
    }

    private ResultActions addAddendum(RequestPostProcessor as, String consultationId, String body) throws Exception {
        return mockMvc.perform(post("/api/consultations/" + consultationId + "/addenda").with(as)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    @Test
    void theAuthorAddsAnAddendumThatTheRecordShowsUnderTheConsultation() throws Exception {
        String consultation = recordedConsultation();

        addAddendum(asArias, consultation, """
                {"text": "La dosis del enalapril es 5 mg."}
                """)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.notes").value("Control de hipertensión."))
                .andExpect(jsonPath("$.addenda", hasSize(1)))
                .andExpect(jsonPath("$.addenda[0].practitionerName").value("Dra. Arias"))
                .andExpect(jsonPath("$.addenda[0].writtenAt").value("2026-03-10T00:00:00"));

        readRecord(asArias)
                .andExpect(jsonPath("$.consultations[0].items", hasSize(2)))
                .andExpect(jsonPath("$.consultations[0].addenda[0].text").value("La dosis del enalapril es 5 mg."));
    }

    @Test
    void anotherPractitionerWithAccessAddsOneAndOneWithoutAccessIsForbidden() throws Exception {
        String consultation = recordedConsultation();
        CreatedPractitioner benitez = practitioners.workingEveryDay();
        RequestPostProcessor asBenitez = users.userFor(benitez.id());
        String body = """
                {"text": "Visto en interconsulta."}
                """;

        addAddendum(asBenitez, consultation, body).andExpect(status().isForbidden());

        book(benitez, TODAY, "11:00:00");
        addAddendum(asBenitez, consultation, body)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.addenda[0].practitionerId").value(benitez.id().toString()));
    }

    @Test
    void aRetriedAddendumIsAddedOnce() throws Exception {
        String consultation = recordedConsultation();
        String body = """
                {"id": "%s", "text": "Una sola vez."}
                """.formatted(UUID.randomUUID());

        addAddendum(asArias, consultation, body).andExpect(status().isCreated());
        addAddendum(asArias, consultation, body)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.addenda", hasSize(1)));
    }

    @Test
    void anUnknownConsultationIsForbiddenAndReceptionCannotAddAnAddendum() throws Exception {
        String consultation = recordedConsultation();
        String body = """
                {"text": "Aclaración."}
                """;

        addAddendum(asArias, UUID.randomUUID().toString(), body).andExpect(status().isForbidden());
        mockMvc.perform(post("/api/consultations/" + consultation + "/addenda")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isForbidden());
    }

    @Test
    void anAddendumWithoutTextIsA400() throws Exception {
        String consultation = recordedConsultation();

        addAddendum(asArias, consultation, """
                {"text": "  "}
                """)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields.text").exists());
    }
}
