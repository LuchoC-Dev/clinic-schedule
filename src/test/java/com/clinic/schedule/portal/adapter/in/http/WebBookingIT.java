package com.clinic.schedule.portal.adapter.in.http;

import static com.clinic.schedule.Logins.patientUser;
import static com.clinic.schedule.portal.PatientAccountFixture.RECEPTION;
import static com.clinic.schedule.portal.PatientAccountFixture.address;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.context.TestSecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import com.clinic.schedule.appointment.PractitionerFixture;
import com.clinic.schedule.appointment.PractitionerFixture.CreatedPractitioner;
import com.clinic.schedule.portal.KeptAccountMail;
import com.clinic.schedule.portal.PatientAccountFixture;
import com.clinic.schedule.portal.PatientAccountFixture.Patient;
import com.clinic.schedule.portal.domain.BookingConfirmation;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Booking on the web (D-82): the reasons reception opens to it, from tomorrow to 30 days ahead, at the times
 * the agenda offers, three ahead at most; the appointment marked as booked on the web, and confirmed by email
 * to the account. "Today" is 10 March 2026, at midnight; the practitioner works every day from 07:00 to 21:00.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(KeptAccountMail.class)
class WebBookingIT {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper json;

    private PatientAccountFixture accounts;
    private CreatedPractitioner arias;

    @BeforeEach
    void setUp() throws Exception {
        accounts = new PatientAccountFixture(mockMvc, json);
        arias = practitionerBookedOnTheWeb();
    }

    /** One who works every day, whose "consulta" is booked on the web and whose "ecografia" is not. */
    private CreatedPractitioner practitionerBookedOnTheWeb() throws Exception {
        // The practitioners' fixture sends its requests as whoever the test is: reception, for the while.
        TestSecurityContextHolder.setAuthentication(UsernamePasswordAuthenticationToken.authenticated("recepcion",
                null, List.of(new SimpleGrantedAuthority("ROLE_RECEPTION"))));
        CreatedPractitioner practitioner;
        try {
            practitioner = new PractitionerFixture(mockMvc, json).workingEveryDay();
        } finally {
            TestSecurityContextHolder.clearContext();
        }
        mockMvc.perform(put("/api/practitioners/" + practitioner.id() + "/reasons/" + practitioner.consulta())
                        .with(RECEPTION).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"consulta\", \"durationMinutes\": 30, \"webBookable\": true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.webBookable").value(true));
        return practitioner;
    }

    private static RequestPostProcessor as(Patient patient) {
        return patientUser("DNI " + patient.dni());
    }

    private ResultActions book(Patient patient, UUID reason, String startsAt) throws Exception {
        return mockMvc.perform(post("/api/portal/appointments").with(as(patient))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"practitionerId\": \"%s\", \"reasonId\": \"%s\", \"startsAt\": \"%s\"}"
                        .formatted(arias.id(), reason, startsAt)));
    }

    private ResultActions times(Patient patient, UUID reason, String date) throws Exception {
        return mockMvc.perform(get("/api/portal/booking/times").with(as(patient))
                .param("practitionerId", arias.id().toString()).param("reasonId", reason.toString())
                .param("date", date));
    }

    @Test
    void aPatientBooksAReasonOpenedToTheWebAndGetsItsConfirmation() throws Exception {
        Patient patient = accounts.withAccount();
        mockMvc.perform(get("/api/portal/booking/options").with(as(patient)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.firstDay").value("2026-03-11"))
                .andExpect(jsonPath("$.lastDay").value("2026-04-09"))
                .andExpect(jsonPath("$.practitioners[?(@.id == '%s')].reasons[*].name", arias.id())
                        .value(hasItem("consulta")))
                .andExpect(jsonPath("$.practitioners[?(@.id == '%s')].reasons[*].name", arias.id())
                        .value(not(hasItem("ecografia"))));
        times(patient, arias.consulta(), "2026-03-11")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.times[0]").value("07:00"))
                .andExpect(jsonPath("$.times").value(hasItem("10:00")));

        book(patient, arias.consulta(), "2026-03-11T10:00:00")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.startsAt").value("2026-03-11T10:00:00"))
                .andExpect(jsonPath("$.practitioner").value("Dra. Arias"))
                .andExpect(jsonPath("$.confirmationSent").value(true));

        mockMvc.perform(get("/api/portal/appointments").with(as(patient)))
                .andExpect(jsonPath("$.upcoming[0].startsAt").value("2026-03-11T10:00:00"))
                .andExpect(jsonPath("$.upcoming[0].reason").value("consulta"));
        mockMvc.perform(get("/api/appointments").with(RECEPTION).param("practitionerId", arias.id().toString())
                        .param("date", "2026-03-11"))
                .andExpect(jsonPath("$[0].bookedVia").value("WEB"))
                .andExpect(jsonPath("$[0].note").isEmpty());
        times(patient, arias.consulta(), "2026-03-11").andExpect(jsonPath("$.times").value(not(hasItem("10:00"))));

        BookingConfirmation confirmation = KeptAccountMail.CONFIRMATIONS.stream()
                .filter(sent -> sent.startsAt().toString().equals("2026-03-11T10:00")
                        && sent.practitioner().equals("Dra. Arias") && sent.fullName().equals("Ana Pérez"))
                .reduce((first, last) -> last).orElseThrow();
        assertThat(confirmation.address()).startsWith("ana-").endsWith("@mail.com");
    }

    @Test
    void onlyTheReasonsDaysAndTimesTheWebOffers() throws Exception {
        Patient patient = accounts.withAccount();

        times(patient, arias.ecografia(), "2026-03-11").andExpect(status().isConflict());
        times(patient, arias.consulta(), "2026-03-10").andExpect(status().isConflict());
        times(patient, arias.consulta(), "2026-04-10").andExpect(status().isConflict());
        times(patient, arias.consulta(), "2026-04-09").andExpect(status().isOk());
        book(patient, arias.ecografia(), "2026-03-11T10:00:00").andExpect(status().isConflict());
        book(patient, arias.consulta(), "2026-03-10T10:00:00").andExpect(status().isConflict());
        book(patient, arias.consulta(), "2026-03-11T10:05:00").andExpect(status().isConflict());
        book(patient, arias.consulta(), "2026-03-11T06:30:00").andExpect(status().isConflict());
        book(patient, UUID.randomUUID(), "2026-03-11T10:00:00").andExpect(status().isConflict());
    }

    /** The time the agenda no longer offers is refused before booking; the patient's own is the booking's. */
    @Test
    void aTimeSomeoneTookOrOneThePatientHasIsRefused() throws Exception {
        Patient patient = accounts.withAccount();
        book(patient, arias.consulta(), "2026-03-12T10:00:00").andExpect(status().isCreated());
        book(accounts.withAccount(), arias.consulta(), "2026-03-12T10:00:00").andExpect(status().isConflict());

        CreatedPractitioner other = practitionerBookedOnTheWeb();
        String withOther = "{\"practitionerId\": \"%s\", \"reasonId\": \"%s\", \"startsAt\": \"%s\"}";
        mockMvc.perform(post("/api/portal/appointments").with(as(patient)).contentType(MediaType.APPLICATION_JSON)
                        .content(withOther.formatted(other.id(), other.consulta(), "2026-03-12T10:00:00")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PATIENT_BUSY"));
        mockMvc.perform(post("/api/portal/appointments").with(as(patient)).contentType(MediaType.APPLICATION_JSON)
                        .content(withOther.formatted(other.id(), other.consulta(), "2026-03-12T10:30:00")))
                .andExpect(status().isCreated());
    }

    /** One ahead for a patient who registered on the web, until reception sees their document (D-91). */
    @Test
    void oneAppointmentAheadUntilReceptionVerifiesAPatientWhoRegisteredOnTheWeb() throws Exception {
        Patient patient = accounts.registeredOnWeb();
        book(patient, arias.consulta(), "2026-03-16T10:00:00").andExpect(status().isCreated());
        book(patient, arias.consulta(), "2026-03-17T10:00:00").andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(
                        "A patient who registered on the web books 1 ahead at most until reception verifies them"));
        mockMvc.perform(post("/api/appointments").with(RECEPTION).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"patientId\": \"%s\", \"practitionerId\": \"%s\", \"reasonId\": \"%s\", "
                                .formatted(patient.id(), arias.id(), arias.consulta())
                                + "\"startsAt\": \"2026-03-17T10:00:00\"}"))
                .andExpect(status().isCreated());

        mockMvc.perform(put("/api/patients/" + patient.id()).with(RECEPTION).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fullName": "Ana Pérez", "documentType": "DNI", "documentNumber": "%s",
                                 "documentSeen": true}
                                """.formatted(patient.dni())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.identity").value("VERIFIED"))
                .andExpect(jsonPath("$.registeredVia").value("WEB"));
        book(patient, arias.consulta(), "2026-03-18T10:00:00").andExpect(status().isCreated());
        book(patient, arias.consulta(), "2026-03-19T10:00:00").andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(
                        "A patient books on the web while they have fewer than 3 appointments ahead"));
    }

    @Test
    void threeAppointmentsAheadAtMostOnTheWebButReceptionBooksMore() throws Exception {
        Patient patient = accounts.withAccount();
        mockMvc.perform(post("/api/appointments").with(RECEPTION).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"patientId\": \"%s\", \"practitionerId\": \"%s\", \"reasonId\": \"%s\", "
                                .formatted(patient.id(), arias.id(), arias.ecografia())
                                + "\"startsAt\": \"2026-03-13T08:00:00\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.bookedVia").value("RECEPTION"));
        book(patient, arias.consulta(), "2026-03-13T10:00:00").andExpect(status().isCreated());
        book(patient, arias.consulta(), "2026-03-14T10:00:00").andExpect(status().isCreated());

        book(patient, arias.consulta(), "2026-03-15T10:00:00").andExpect(status().isConflict());
        mockMvc.perform(post("/api/appointments").with(RECEPTION).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"patientId\": \"%s\", \"practitionerId\": \"%s\", \"reasonId\": \"%s\", "
                                .formatted(patient.id(), arias.id(), arias.consulta())
                                + "\"startsAt\": \"2026-03-15T10:00:00\"}"))
                .andExpect(status().isCreated());
    }

    @Test
    void anAppointmentIsBookedEvenWhenItsConfirmationDoesNotGoOut() throws Exception {
        Patient patient = accounts.withAccount();
        mockMvc.perform(post("/api/patient-accounts/" + accounts.accountOf(patient) + "/email-change").with(RECEPTION)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"email\": \"falla-" + address() + "\"}"))
                .andExpect(status().isOk());

        book(patient, arias.consulta(), "2026-03-16T10:00:00")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.confirmationSent").value(false));
    }

    @Test
    void onlyAPatientBooksForThemselvesOnTheWeb() throws Exception {
        mockMvc.perform(get("/api/portal/booking/options").with(RECEPTION)).andExpect(status().isForbidden());
        mockMvc.perform(post("/api/portal/appointments").with(RECEPTION).contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void receptionOpensAReasonToTheWebAndClosesIt() throws Exception {
        mockMvc.perform(put("/api/practitioners/" + arias.id() + "/reasons/" + arias.ecografia()).with(RECEPTION)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"ecografia\", \"durationMinutes\": 45, \"webBookable\": true}"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/practitioners/" + arias.id()).with(RECEPTION))
                .andExpect(jsonPath("$.reasons[?(@.name == 'ecografia')].webBookable").value(hasItem(true)));

        mockMvc.perform(put("/api/practitioners/" + arias.id() + "/reasons/" + arias.ecografia()).with(RECEPTION)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"ecografia\", \"durationMinutes\": 45}"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/practitioners/" + arias.id()).with(RECEPTION))
                .andExpect(jsonPath("$.reasons[?(@.name == 'ecografia')].webBookable").value(hasItem(false)));

        mockMvc.perform(put("/api/practitioners/" + arias.id() + "/reasons/" + arias.consulta()).with(RECEPTION)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"consulta\", \"durationMinutes\": 30}"))
                .andExpect(jsonPath("$.webBookable").value(false));
    }
}
