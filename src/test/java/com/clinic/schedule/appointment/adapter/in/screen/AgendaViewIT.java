package com.clinic.schedule.appointment.adapter.in.screen;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.startsWith;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.net.URI;
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
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Reception's agenda screens over the real stack. They are thin (D-02), so what is worth
 * checking is that they reach the use cases and say the right thing in Spanish.
 */
@SpringBootTest
@AutoConfigureMockMvc
@WithMockUser(roles = "RECEPTION")
class AgendaViewIT {

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

    private String bookThroughTheApi(CreatedPractitioner practitioner, UUID patient, String time) throws Exception {
        return bookThroughTheApi(practitioner, patient, DAY, time);
    }

    private String bookThroughTheApi(CreatedPractitioner practitioner, UUID patient, String day, String time)
            throws Exception {
        MvcResult result = mockMvc.perform(post("/api/appointments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"patientId": "%s", "practitionerId": "%s", "reasonId": "%s", "startsAt": "%sT%s"}
                                """.formatted(patient, practitioner.id(), practitioner.consulta(), day, time)))
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
    void arrivingFromTheAgendaBringsThePatientIntoTheQueue() throws Exception {
        CreatedPractitioner arias = practitioners.workingEveryDay();
        String id = bookThroughTheApi(arias, patients.newPatient(), "10:00:00");
        new ArrivalFixture(mockMvc, jdbc).openQueueFor(arias.id());

        // "Llegó" is the queues' (D-65): back to the same agenda, with the patient in the queue.
        mockMvc.perform(post("/arrivals/appointment-arrivals/" + id).with(csrf())
                        .param("practitionerId", arias.id().toString()).param("date", DAY).param("amount", "15000"))
                .andExpect(redirectedUrlPattern("/agenda?*"))
                .andExpect(flash().attribute("message", "Llegó: está en la cola de su profesional."));

        mockMvc.perform(get("/agenda").param("practitionerId", arias.id().toString()).param("date", DAY))
                .andExpect(content().string(containsString("Llegó a las")));
    }

    @Test
    void aRefusalComesBackAsAMessageInSpanish() throws Exception {
        CreatedPractitioner arias = practitioners.workingEveryDay();
        String id = bookThroughTheApi(arias, patients.newPatient(), "10:00:00");
        mockMvc.perform(post("/agenda/appointments/" + id + "/cancel").param("requestedBy", "PATIENT").with(csrf())
                .param("practitionerId", arias.id().toString()).param("date", DAY));

        mockMvc.perform(post("/agenda/appointments/" + id + "/cancel").param("requestedBy", "PATIENT").with(csrf())
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
    void bookingWithoutChoosingAPatientAsksForItAndKeepsTheTime() throws Exception {
        CreatedPractitioner arias = practitioners.workingEveryDay();

        mockMvc.perform(post("/agenda/new").with(csrf())
                        .param("practitionerId", arias.id().toString())
                        .param("date", DAY)
                        .param("reasonId", arias.consulta().toString())
                        .param("time", "11:00"))
                .andExpect(redirectedUrlPattern("/agenda/new?*time=11:00*"))
                .andExpect(flash().attribute("error", "Elegí un paciente."));
    }

    @Test
    void bookingWithNothingChosenSaysEverythingThatIsMissing() throws Exception {
        CreatedPractitioner arias = practitioners.workingEveryDay();

        mockMvc.perform(post("/agenda/new").with(csrf())
                        .param("practitionerId", arias.id().toString())
                        .param("date", DAY)
                        .param("reasonId", "")
                        .param("otherTime", ""))
                .andExpect(flash().attribute("error", "Elegí la razón, un paciente y un horario."));
    }

    /**
     * The bug reception hit: the patient search and the booking were two forms, so searching
     * lost the time and booking never carried the patient. Refreshing now carries everything
     * back to the screen, where it stays chosen.
     */
    @Test
    void refreshingTheBookingScreenKeepsWhatWasChosen() throws Exception {
        CreatedPractitioner arias = practitioners.workingEveryDay();
        String name = "Refresco " + UUID.randomUUID().toString().substring(0, 6);
        String patient = registerPatientNamed(name);

        MvcResult refreshed = mockMvc.perform(post("/agenda/new").with(csrf())
                        .param("action", "refresh")
                        .param("practitionerId", arias.id().toString())
                        .param("date", DAY)
                        .param("reasonId", arias.consulta().toString())
                        .param("patientQuery", "zzz nadie")
                        .param("patientId", patient)
                        .param("time", "11:00")
                        .param("note", "trae estudios"))
                .andExpect(redirectedUrlPattern("/agenda/new?*"))
                .andReturn();

        mockMvc.perform(get(URI.create(refreshed.getResponse().getRedirectedUrl())))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(name)))
                .andExpect(content().string(containsString("value=\"" + patient + "\" checked=\"checked\"")))
                .andExpect(content().string(containsString("value=\"11:00\" checked=\"checked\"")))
                .andExpect(content().string(containsString("trae estudios")));
    }

    @Test
    void aSearchWithASingleMatchChoosesThatPatient() throws Exception {
        CreatedPractitioner arias = practitioners.workingEveryDay();
        String name = "Unica " + UUID.randomUUID().toString().substring(0, 8);
        String patient = registerPatientNamed(name);

        mockMvc.perform(get("/agenda/new")
                        .param("practitionerId", arias.id().toString())
                        .param("date", DAY)
                        .param("patientQuery", name))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("value=\"" + patient + "\" checked=\"checked\"")));
    }

    @Test
    void theFirstReasonComesChosenSoTheFreeTimesShowAtOnce() throws Exception {
        CreatedPractitioner arias = practitioners.workingEveryDay();

        mockMvc.perform(get("/agenda/new").param("practitionerId", arias.id().toString()).param("date", DAY))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("name=\"time\"")))
                .andExpect(content().string(containsString("data-refresh")));
    }

    // --- a new patient on the booking screen (D-37) -------------------------------------

    private static String randomDni() {
        return String.valueOf(10_000_000 + (long) (Math.random() * 80_000_000));
    }

    @Test
    void bookingANewPatientRegistersThemAndBooks() throws Exception {
        CreatedPractitioner arias = practitioners.workingEveryDay();
        String name = "Nuevo " + UUID.randomUUID().toString().substring(0, 8);
        String dni = randomDni();

        mockMvc.perform(post("/agenda/new").with(csrf())
                        .param("practitionerId", arias.id().toString())
                        .param("date", DAY)
                        .param("reasonId", arias.consulta().toString())
                        .param("newPatientName", name)
                        .param("newPatientDocumentType", "DNI")
                        .param("newPatientDocumentNumber", dni)
                        .param("time", "12:00"))
                .andExpect(redirectedUrlPattern("/agenda?*"))
                .andExpect(flash().attribute("message", "Paciente registrado y turno reservado."));

        mockMvc.perform(get("/agenda").param("practitionerId", arias.id().toString()).param("date", DAY))
                .andExpect(content().string(containsString(name + " — DNI " + dni)));
    }

    @Test
    void aNewPatientWithSomeoneElsesDocumentIsNotRegisteredNorBooked() throws Exception {
        CreatedPractitioner arias = practitioners.workingEveryDay();
        String dni = randomDni();
        mockMvc.perform(post("/api/patients").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fullName": "Ya Estaba", "documentType": "DNI", "documentNumber": "%s"}
                                """.formatted(dni)))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/agenda/new").with(csrf())
                        .param("practitionerId", arias.id().toString())
                        .param("date", DAY)
                        .param("reasonId", arias.consulta().toString())
                        .param("newPatientName", "Otro Nombre")
                        .param("newPatientDocumentType", "DNI")
                        .param("newPatientDocumentNumber", dni)
                        .param("time", "12:30"))
                .andExpect(result -> assertThat(result.getResponse().getRedirectedUrl()).doesNotContain("Otro"))
                .andExpect(result -> assertThat(((NewPatientForm) result.getFlashMap().get("newPatient")).name())
                        .isEqualTo("Otro Nombre"))
                .andExpect(flash().attribute("error", containsString("ya pertenece a otro paciente")));

        mockMvc.perform(get("/api/appointments").param("practitionerId", arias.id().toString()).param("date", DAY))
                .andExpect(content().string(not(containsString(DAY + "T12:30:00"))));
    }

    @Test
    void aNewPatientWithAMalformedDniIsRefusedInSpanish() throws Exception {
        CreatedPractitioner arias = practitioners.workingEveryDay();

        mockMvc.perform(post("/agenda/new").with(csrf())
                        .param("practitionerId", arias.id().toString())
                        .param("date", DAY)
                        .param("reasonId", arias.consulta().toString())
                        .param("newPatientName", "Letras En El Dni")
                        .param("newPatientDocumentType", "DNI")
                        .param("newPatientDocumentNumber", "12AB")
                        .param("time", "12:00"))
                .andExpect(flash().attribute("error", containsString("el DNI, 7 u 8 dígitos")));
    }

    @Test
    void whenTheBookingIsRefusedNobodyIsRegisteredAndTheNewPatientStaysTypedIn() throws Exception {
        CreatedPractitioner arias = practitioners.workingEveryDay();
        bookThroughTheApi(arias, patients.newPatient(), "16:00:00");
        String name = "Sin Lugar " + UUID.randomUUID().toString().substring(0, 8);

        MvcResult refused = mockMvc.perform(post("/agenda/new").with(csrf())
                        .param("practitionerId", arias.id().toString())
                        .param("date", DAY)
                        .param("reasonId", arias.consulta().toString())
                        .param("newPatientName", name)
                        .param("newPatientPhoneNumber", "1145556666")
                        .param("time", "16:00"))
                .andExpect(result -> assertThat(result.getResponse().getRedirectedUrl())
                        .doesNotContain("patientId").doesNotContain("Lugar").doesNotContain("1145556666"))
                .andExpect(flash().attribute("error", "El profesional ya tiene un turno en ese horario."))
                .andReturn();

        mockMvc.perform(get("/api/patients").param("query", name)).andExpect(jsonPath("$", hasSize(0)));
        mockMvc.perform(get(URI.create(refused.getResponse().getRedirectedUrl()))
                        .flashAttrs(refused.getFlashMap()))
                .andExpect(content().string(containsString("value=\"" + name + "\"")))
                .andExpect(content().string(containsString("value=\"1145556666\"")));
    }

    @Test
    void refreshingKeepsTheNewPatientTypedInOutOfTheAddress() throws Exception {
        CreatedPractitioner arias = practitioners.workingEveryDay();

        mockMvc.perform(post("/agenda/new").with(csrf())
                        .param("action", "refresh")
                        .param("practitionerId", arias.id().toString())
                        .param("date", DAY)
                        .param("newPatientName", "Todavia Eligiendo")
                        .param("newPatientEmailAddress", "eligiendo@mail.com"))
                .andExpect(result -> assertThat(result.getResponse().getRedirectedUrl())
                        .doesNotContain("Eligiendo").doesNotContain("eligiendo"))
                .andExpect(result -> assertThat((NewPatientForm) result.getFlashMap().get("newPatient"))
                        .extracting(NewPatientForm::name, NewPatientForm::emailAddress)
                        .containsExactly("Todavia Eligiendo", "eligiendo@mail.com"));
    }

    @Test
    void aNewPatientIsBookedWithTheirPhoneAndEmailEachWithItsOwner() throws Exception {
        CreatedPractitioner arias = practitioners.workingEveryDay();
        String name = "Con Contacto " + UUID.randomUUID().toString().substring(0, 8);

        mockMvc.perform(post("/agenda/new").with(csrf())
                        .param("practitionerId", arias.id().toString())
                        .param("date", DAY)
                        .param("reasonId", arias.consulta().toString())
                        .param("newPatientName", name)
                        .param("newPatientPhoneNumber", "11 4555-6666")
                        .param("newPatientPhoneOwnerRelation", "MOTHER")
                        .param("newPatientPhoneOwnerName", "Ana Perez")
                        .param("newPatientEmailAddress", "Nene@Mail.com")
                        .param("time", "12:00"))
                .andExpect(redirectedUrlPattern("/agenda?*"))
                .andExpect(flash().attribute("message", "Paciente registrado y turno reservado."));

        mockMvc.perform(get("/api/patients").param("query", name))
                .andExpect(jsonPath("$[0].phone.number").value("1145556666"))
                .andExpect(jsonPath("$[0].phone.owner.relation").value("MOTHER"))
                .andExpect(jsonPath("$[0].email.address").value("nene@mail.com"))
                .andExpect(jsonPath("$[0].email.owner").isEmpty());
    }

    @Test
    void aPhoneTypedWithoutANameIsANewPatientWithoutAName() throws Exception {
        CreatedPractitioner arias = practitioners.workingEveryDay();

        mockMvc.perform(post("/agenda/new").with(csrf())
                        .param("practitionerId", arias.id().toString())
                        .param("date", DAY)
                        .param("reasonId", arias.consulta().toString())
                        .param("newPatientPhoneNumber", "1145556666")
                        .param("time", "12:00"))
                .andExpect(flash().attribute("error", startsWith("Revisá el paciente nuevo: el nombre")));
    }

    @Test
    void aNewPatientWithAWrongPhoneIsNeitherRegisteredNorBooked() throws Exception {
        CreatedPractitioner arias = practitioners.workingEveryDay();
        String name = "Mal Telefono " + UUID.randomUUID().toString().substring(0, 8);

        mockMvc.perform(post("/agenda/new").with(csrf())
                        .param("practitionerId", arias.id().toString())
                        .param("date", DAY)
                        .param("reasonId", arias.consulta().toString())
                        .param("newPatientName", name)
                        .param("newPatientPhoneNumber", "123")
                        .param("time", "12:00"))
                .andExpect(flash().attribute("error", startsWith("Revisá el contacto del paciente nuevo")));

        mockMvc.perform(get("/api/patients").param("query", name)).andExpect(jsonPath("$", hasSize(0)));
        mockMvc.perform(get("/api/appointments").param("practitionerId", arias.id().toString()).param("date", DAY))
                .andExpect(content().string(not(containsString(DAY + "T12:00:00"))));
    }

    @Test
    void aChosenPatientAndANewOneAtOnceAreRefused() throws Exception {
        CreatedPractitioner arias = practitioners.workingEveryDay();

        mockMvc.perform(post("/agenda/new").with(csrf())
                        .param("practitionerId", arias.id().toString())
                        .param("date", DAY)
                        .param("reasonId", arias.consulta().toString())
                        .param("patientId", patients.newPatient().toString())
                        .param("newPatientName", "Otro Mas")
                        .param("time", "12:00"))
                .andExpect(flash().attribute("error", "Elegiste un paciente y también cargaste uno nuevo: dejá solo uno."));
    }

    // --- the views ----------------------------------------------------------------------

    @Test
    void withoutAPractitionerTheAgendaShowsEveryoneWithAPractitionerColumn() throws Exception {
        String ana = "Ana Todos " + UUID.randomUUID().toString().substring(0, 6);
        String juan = "Juan Todos " + UUID.randomUUID().toString().substring(0, 6);
        CreatedPractitioner arias = practitioners.workingEveryDay();
        bookThroughTheApi(arias, UUID.fromString(registerPatientNamed(ana)), "10:00:00");
        bookThroughTheApi(practitioners.workingEveryDay(), UUID.fromString(registerPatientNamed(juan)), "10:00:00");

        mockMvc.perform(get("/agenda").param("date", DAY))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(ana)))
                .andExpect(content().string(containsString(juan)))
                .andExpect(content().string(containsString("<th>Profesional</th>")))
                .andExpect(content().string(containsString("Agenda de todos los profesionales")))
                // The agenda opens on everyone's, so booking must be reachable from it, with the
                // practitioner left to choose on the booking screen (D-45).
                .andExpect(content().string(containsString("href=\"/agenda/new?date=" + DAY + "\"")))
                .andExpect(content().string(not(containsString("Nuevo turno con "))));
    }

    // --- one practitioner at a time, and never a doubt about which (D-45) ----------------

    @Test
    void aPractitionersAgendaSaysWhoseItIsAndBooksWithThem() throws Exception {
        CreatedPractitioner arias = practitioners.workingEveryDay();

        mockMvc.perform(get("/agenda").param("practitionerId", arias.id().toString()).param("date", DAY))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("<h1>Agenda de Dra. Arias</h1>")))
                .andExpect(content().string(containsString(
                        "href=\"/agenda/new?practitionerId=" + arias.id() + "&amp;date=" + DAY + "\"")))
                .andExpect(content().string(containsString("Nuevo turno con Dra. Arias")));
    }

    @Test
    void theAgendasChoicesRefreshTheScreenAsTheyChange() throws Exception {
        mockMvc.perform(get("/agenda").param("date", DAY))
                .andExpect(content().string(containsString("<select name=\"practitionerId\" data-refresh>")))
                .andExpect(content().string(containsString("<select name=\"view\" data-refresh>")))
                .andExpect(content().string(containsString("data-refresh-button")));
    }

    @Test
    void theBookingScreenOpensWithoutAPractitionerAndAsksForOne() throws Exception {
        CreatedPractitioner arias = practitioners.workingEveryDay();

        mockMvc.perform(get("/agenda/new").param("date", DAY))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("<h1>Nuevo turno</h1>")))
                .andExpect(content().string(containsString("<option value=\"\">— elegir —</option>")))
                .andExpect(content().string(containsString("value=\"" + arias.id() + "\"")))
                .andExpect(content().string(containsString("Elegí el profesional para ver sus horarios libres.")))
                .andExpect(content().string(not(containsString("name=\"time\""))));
    }

    @Test
    void bookingWithoutAPractitionerAsksForIt() throws Exception {
        mockMvc.perform(post("/agenda/new").with(csrf())
                        .param("date", DAY)
                        .param("patientId", patients.newPatient().toString())
                        .param("time", "11:00"))
                .andExpect(redirectedUrlPattern("/agenda/new?*"))
                .andExpect(flash().attribute("error", "Elegí el profesional y la razón."));
    }

    @Test
    void changingThePractitionerKeepsWhatWasChosenAndTakesTheirReasons() throws Exception {
        CreatedPractitioner arias = practitioners.workingEveryDay();
        CreatedPractitioner benitez = practitioners.workingEveryDay();
        String name = "Cambio " + UUID.randomUUID().toString().substring(0, 6);
        String patient = registerPatientNamed(name);

        MvcResult refreshed = mockMvc.perform(post("/agenda/new").with(csrf())
                        .param("action", "refresh")
                        .param("practitionerId", benitez.id().toString())
                        .param("date", DAY)
                        .param("reasonId", arias.consulta().toString())
                        .param("patientId", patient)
                        .param("note", "cambió de doctor"))
                .andReturn();

        mockMvc.perform(get(URI.create(refreshed.getResponse().getRedirectedUrl())))
                .andExpect(status().isOk())
                // Arias's reason is not Benítez's, so his first comes chosen instead.
                .andExpect(content().string(containsString("value=\"" + benitez.consulta() + "\"\n")))
                .andExpect(content().string(containsString("value=\"" + patient + "\" checked=\"checked\"")))
                .andExpect(content().string(containsString("cambió de doctor")))
                .andExpect(content().string(containsString("name=\"time\"")));
    }

    @Test
    void aDayThePractitionerDoesNotWorkIsSaidSo() throws Exception {
        CreatedPractitioner arias = practitioners.workingTuesdaysWithALunchBreak();

        // 2026-03-11 is a Wednesday.
        mockMvc.perform(get("/agenda/new")
                        .param("practitionerId", arias.id().toString())
                        .param("date", "2026-03-11")
                        .param("reasonId", arias.consulta().toString()))
                .andExpect(content().string(containsString("Dra. Arias no atiende ese día.")))
                .andExpect(content().string(not(containsString("No quedan horarios libres"))));
    }

    @Test
    void aFullDayIsNotMistakenForADayOff() throws Exception {
        CreatedPractitioner arias = practitioners.workingTuesdaysWithALunchBreak();
        for (String time : new String[] {"09:00:00", "09:30:00", "10:00:00", "10:30:00", "11:00:00", "11:30:00",
                "12:00:00", "12:30:00", "14:00:00", "14:30:00", "15:00:00", "15:30:00", "16:00:00", "16:30:00",
                "17:00:00", "17:30:00"}) {
            bookThroughTheApi(arias, patients.newPatient(), time);
        }

        mockMvc.perform(get("/agenda/new")
                        .param("practitionerId", arias.id().toString())
                        .param("date", DAY)
                        .param("reasonId", arias.consulta().toString()))
                .andExpect(content().string(containsString("No quedan horarios libres ese día para esta razón.")))
                .andExpect(content().string(not(containsString("no atiende ese día"))));
    }

    @Test
    void theWeekShowsEveryDayFromMondayToSunday() throws Exception {
        CreatedPractitioner arias = practitioners.workingEveryDay();
        String tuesday = "Martes " + UUID.randomUUID().toString().substring(0, 6);
        String thursday = "Jueves " + UUID.randomUUID().toString().substring(0, 6);
        bookThroughTheApi(arias, UUID.fromString(registerPatientNamed(tuesday)), DAY, "10:00:00");
        bookThroughTheApi(arias, UUID.fromString(registerPatientNamed(thursday)), "2026-03-12", "10:00:00");

        mockMvc.perform(get("/agenda").param("practitionerId", arias.id().toString())
                        .param("date", DAY).param("view", "week"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("lunes 9/03")))
                .andExpect(content().string(containsString("domingo 15/03")))
                .andExpect(content().string(containsString(tuesday)))
                .andExpect(content().string(containsString(thursday)))
                .andExpect(content().string(containsString("date=2026-03-17")));
    }

    @Test
    void theDayLinksToTheDaysAroundIt() throws Exception {
        CreatedPractitioner arias = practitioners.workingEveryDay();

        mockMvc.perform(get("/agenda").param("practitionerId", arias.id().toString()).param("date", DAY))
                .andExpect(content().string(containsString("date=2026-03-09")))
                .andExpect(content().string(containsString("date=2026-03-11")))
                .andExpect(content().string(containsString("Día siguiente")));
    }

    @Test
    void actingFromEveryonesWeekReturnsToIt() throws Exception {
        CreatedPractitioner arias = practitioners.workingEveryDay();
        String id = bookThroughTheApi(arias, patients.newPatient(), "10:00:00");

        mockMvc.perform(post("/agenda/appointments/" + id + "/cancel").param("requestedBy", "PATIENT").with(csrf())
                        .param("practitionerId", "").param("date", DAY).param("view", "week"))
                .andExpect(redirectedUrl("/agenda?date=" + DAY + "&view=week"));
    }

    @Test
    void aPatientsAppointmentsIncludeTheCancelledOnes() throws Exception {
        CreatedPractitioner arias = practitioners.workingEveryDay();
        String name = "Historial " + UUID.randomUUID().toString().substring(0, 6);
        UUID patient = UUID.fromString(registerPatientNamed(name));
        String cancelled = bookThroughTheApi(arias, patient, "09:00:00");
        bookThroughTheApi(arias, patient, "2026-03-12", "10:00:00");
        mockMvc.perform(post("/api/appointments/" + cancelled + "/cancel")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"requestedBy\":\"PATIENT\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/agenda/patients/" + patient))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Turnos de " + name)))
                .andExpect(content().string(containsString("Cancelado")))
                .andExpect(content().string(containsString("Reservado")))
                .andExpect(content().string(containsString("jue 12/03/2026")));
    }

    @Test
    void anUnknownPatientsAppointmentsLeadBackToThePatients() throws Exception {
        mockMvc.perform(get("/agenda/patients/" + UUID.randomUUID()))
                .andExpect(redirectedUrl("/patients"))
                .andExpect(flash().attribute("error", "No se encontró el paciente."));
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

    // --- protections (D-38) -------------------------------------------------------------

    @Test
    void aNoteTooLongComesBackAsAMessageInsteadOfFailing() throws Exception {
        CreatedPractitioner arias = practitioners.workingEveryDay();

        mockMvc.perform(post("/agenda/new").with(csrf())
                        .param("practitionerId", arias.id().toString())
                        .param("date", DAY)
                        .param("reasonId", arias.consulta().toString())
                        .param("patientId", patients.newPatient().toString())
                        .param("time", "13:00")
                        .param("note", "x".repeat(600)))
                .andExpect(redirectedUrlPattern("/agenda/new?*"))
                .andExpect(flash().attribute("error", "Los datos ingresados no son válidos."));
    }

    @Test
    void bookingATimeAlreadyGoneSaysSo() throws Exception {
        CreatedPractitioner arias = practitioners.workingEveryDay();

        mockMvc.perform(post("/agenda/new").with(csrf())
                        .param("practitionerId", arias.id().toString())
                        .param("date", "2026-03-09")
                        .param("reasonId", arias.consulta().toString())
                        .param("patientId", patients.newPatient().toString())
                        .param("time", "10:00"))
                .andExpect(flash().attribute("error", "Ese horario ya pasó."));
    }

    @Test
    void bookingMoreThanAYearAheadAsksToCheckTheYear() throws Exception {
        CreatedPractitioner arias = practitioners.workingEveryDay();

        mockMvc.perform(post("/agenda/new").with(csrf())
                        .param("practitionerId", arias.id().toString())
                        .param("date", "2027-03-12")
                        .param("reasonId", arias.consulta().toString())
                        .param("patientId", patients.newPatient().toString())
                        .param("time", "10:00"))
                .andExpect(flash().attribute("error",
                        "No se reservan turnos con más de un año de anticipación: revisá el año."));
    }

    @Test
    void aLateArrivalNoticeOnAnotherDaySaysItIsOnlyForTheDay() throws Exception {
        CreatedPractitioner arias = practitioners.workingEveryDay();
        String id = bookThroughTheApi(arias, patients.newPatient(), "2026-03-12", "10:00:00");

        mockMvc.perform(post("/agenda/appointments/" + id + "/late-notice").with(csrf())
                        .param("practitionerId", arias.id().toString()).param("date", "2026-03-12"))
                .andExpect(flash().attribute("error", "Eso solo se puede hacer el día del turno."));
    }

    /** The API's error body is for the API: a screen asked for with a malformed id does not get it. */
    @Test
    void aMalformedIdOnAScreenIsNotAnsweredWithTheApisErrorBody() throws Exception {
        mockMvc.perform(get("/agenda").param("practitionerId", "not-an-id"))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(not(containsString("has an invalid value"))));
    }
}
