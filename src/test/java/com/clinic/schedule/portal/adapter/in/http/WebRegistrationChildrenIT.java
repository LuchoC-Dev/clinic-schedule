package com.clinic.schedule.portal.adapter.in.http;

import static com.clinic.schedule.portal.PatientAccountFixture.PASSWORD;
import static com.clinic.schedule.portal.PatientAccountFixture.RECEPTION;
import static com.clinic.schedule.portal.PatientAccountFixture.address;
import static com.clinic.schedule.portal.PatientAccountFixture.dni;
import static com.clinic.schedule.portal.PatientAccountFixture.lastRegistrationTokenTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.StreamSupport;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.context.TestSecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import com.clinic.schedule.appointment.PractitionerFixture;
import com.clinic.schedule.appointment.PractitionerFixture.CreatedPractitioner;
import com.clinic.schedule.portal.KeptAccountMail;
import com.clinic.schedule.portal.PatientAccountFixture;
import com.clinic.schedule.portal.PatientAccountFixture.Patient;
import com.clinic.schedule.portal.application.usecase.RegisterOnWebUseCase;
import com.clinic.schedule.portal.domain.RegistrationMessage;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * An adult registers their children under 13 on the web over the real stack (D-92): with their own registration or
 * later from their account, into a family group with the relation not proved; a document the practice knows registers
 * nothing and is answered the same; the adult acts for the children, one appointment ahead each, until reception
 * verifies them and proves the relation. "Today" is 10 March 2026, at midnight, and the tests' clock stands still.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(KeptAccountMail.class)
class WebRegistrationChildrenIT {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper json;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private RegisterOnWebUseCase registrations;

    private PatientAccountFixture accounts;

    @BeforeEach
    void setUp() {
        accounts = new PatientAccountFixture(mockMvc, json);
    }

    private static String child(String name, String dni, String birthDate, String relation) {
        return """
                {"fullName": "%s", "documentType": "DNI", "documentNumber": "%s", "birthDate": "%s",
                 "relation": "%s"}""".formatted(name, dni, birthDate, relation);
    }

    private static String registration(String dni, String email, String... children) {
        return """
                {"fullName": "Ana Pérez", "documentType": "DNI", "documentNumber": "%s", "birthDate": "1990-05-14",
                 "email": "%s", "password": "%s", "children": [%s]}
                """.formatted(dni, email, PASSWORD, String.join(", ", children));
    }

    private ResultActions register(String body) throws Exception {
        return mockMvc.perform(post("/api/portal/registrations").contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private ResultActions confirm(String token) throws Exception {
        return mockMvc.perform(post("/api/portal/registrations/confirmation").contentType(MediaType.APPLICATION_JSON)
                .content("{\"token\": \"%s\"}".formatted(token)));
    }

    private JsonNode read(ResultActions actions) throws Exception {
        return json.readTree(actions.andReturn().getResponse().getContentAsString());
    }

    private String idOf(String dni) throws Exception {
        JsonNode found = read(mockMvc.perform(get("/api/patients").with(RECEPTION).param("query", dni)));
        assertThat(found).hasSize(1);
        return found.get(0).get("id").asText();
    }

    private JsonNode patientsOf(String dni) throws Exception {
        return read(mockMvc.perform(get("/api/patients").with(RECEPTION).param("query", dni)));
    }

    private JsonNode groupsOf(String patientId) throws Exception {
        return read(mockMvc.perform(get("/api/family-groups").with(RECEPTION).param("patientId", patientId)));
    }

    private JsonNode memberOf(JsonNode group, String patientId) {
        return StreamSupport.stream(group.get("members").spliterator(), false)
                .filter(member -> member.get("patientId").asText().equals(patientId))
                .findFirst().orElseThrow();
    }

    private static RequestPostProcessor as(String dni) {
        return httpBasic("DNI " + dni, PASSWORD);
    }

    private int waitingChildren() {
        return jdbc.queryForObject("select count(*) from web_registration_child", Integer.class);
    }

    private RegistrationMessage lastLinkTo(String email) {
        return KeptAccountMail.REGISTRATIONS.stream().filter(sent -> sent.address().equals(email))
                .reduce((first, last) -> last).orElseThrow();
    }

    /** An adult registered on the web with two children, confirmed: their DNIs, the adult's first. */
    private List<String> registeredWithTwoChildren() throws Exception {
        String adult = dni();
        String tomas = dni();
        String sofia = dni();
        String email = address();
        register(registration(adult, email, child("Tomás Pérez", tomas, "2018-04-02", "MOTHER"),
                child("Sofía Pérez", sofia, "2020-09-30", "MOTHER"))).andExpect(status().isAccepted());
        confirm(lastRegistrationTokenTo(email)).andExpect(status().isOk());
        return List.of(adult, tomas, sofia);
    }

    @Test
    void anAdultRegistersTheirChildrenAndAKnownChildsDocumentIsSkippedTheSame() throws Exception {
        Patient known = accounts.patient("Juan Pérez", false, "2017-02-02");
        String adult = dni();
        String tomas = dni();
        String sofia = dni();
        String email = address();
        MvcResult withChildren = register(registration(adult, email,
                child(" Tomás Pérez ", tomas.substring(0, 2) + "." + tomas.substring(2), "2018-04-02", "MOTHER"),
                child("Juan Pérez", known.dni(), "2017-02-02", "MOTHER"),
                child("Sofía Pérez", sofia, "2020-09-30", "GUARDIAN"))).andReturn();
        MvcResult alone = register(registration(dni(), address())).andReturn();
        assertThat(withChildren.getResponse().getStatus()).isEqualTo(202).isEqualTo(alone.getResponse().getStatus());
        assertThat(withChildren.getResponse().getContentAsString()).isEqualTo(alone.getResponse().getContentAsString());

        RegistrationMessage sent = lastLinkTo(email);
        assertThat(sent.children()).containsExactly("Tomás Pérez", "Sofía Pérez");
        assertThat(sent.notRegistered()).containsExactly("Juan Pérez");
        assertThat(jdbc.queryForList("""
                select c.full_name from web_registration_child c join web_registration r on r.id = c.registration_id
                where r.email = ? order by c.position""", String.class, email))
                .containsExactly("Tomás Pérez", "Sofía Pérez");
        assertThat(patientsOf(tomas)).isEmpty();

        confirm(sent.token()).andExpect(status().isOk()).andExpect(jsonPath("$.notRegistered").isEmpty());

        String adultId = idOf(adult);
        String tomasId = idOf(tomas);
        String sofiaId = idOf(sofia);
        JsonNode tomasPatient = patientsOf(tomas).get(0);
        assertThat(tomasPatient.get("fullName").asText()).isEqualTo("Tomás Pérez");
        assertThat(tomasPatient.get("identity").asText()).isEqualTo("DECLARED");
        assertThat(tomasPatient.get("registeredVia").asText()).isEqualTo("WEB");
        assertThat(tomasPatient.get("birthDate").asText()).isEqualTo("2018-04-02");
        assertThat(tomasPatient.get("document").get("number").asText()).isEqualTo(tomas);
        mockMvc.perform(get("/api/patient-accounts").with(RECEPTION).param("patientId", tomasId))
                .andExpect(status().isNotFound());

        JsonNode groups = groupsOf(adultId);
        assertThat(groups).hasSize(1);
        JsonNode group = groups.get(0);
        assertThat(group.get("name").asText()).isEqualTo("Familia de Ana Pérez");
        assertThat(group.get("members")).hasSize(3);
        assertThat(memberOf(group, adultId).get("shares").asBoolean()).isTrue();
        assertThat(memberOf(group, adultId).get("relationProved").asBoolean()).isTrue();
        JsonNode tomasMember = memberOf(group, tomasId);
        assertThat(tomasMember.get("requestedBy").asText()).isEqualTo(adultId);
        assertThat(tomasMember.get("relation").asText()).isEqualTo("MOTHER");
        assertThat(tomasMember.get("relationProved").asBoolean()).isFalse();
        assertThat(memberOf(group, sofiaId).get("relation").asText()).isEqualTo("GUARDIAN");
        JsonNode history = read(mockMvc.perform(get("/api/family-groups/" + group.get("id").asText() + "/changes")
                .with(RECEPTION)));
        assertThat(history.findValuesAsText("action"))
                .containsExactlyInAnyOrder("CREATED", "JOINED", "CHILD_ADDED", "CHILD_ADDED");
        String account = accounts.accountOf(new Patient(adultId, adult));
        assertThat(history.findValuesAsText("by")).containsOnly("portal:" + account);

        // The known child is left as they were, in no group.
        assertThat(groupsOf(known.id())).isEmpty();
        assertThat(waitingChildren()).isZero();

        // The adult acts for both children at once.
        mockMvc.perform(get("/api/portal/booking/options").with(as(adult)))
                .andExpect(jsonPath("$.patients[*].id").value(hasItem(tomasId)))
                .andExpect(jsonPath("$.patients[*].id").value(hasItem(sofiaId)));
        mockMvc.perform(get("/api/portal/family").with(as(adult)))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Tomás Pérez")));
    }

    @Test
    void aChildsDocumentRegisteredMeanwhileIsSkippedAtConfirmationAndNamed() throws Exception {
        String adult = dni();
        String tomas = dni();
        String email = address();
        register(registration(adult, email, child("Tomás Pérez", tomas, "2018-04-02", "FATHER")))
                .andExpect(status().isAccepted());
        // Reception registers Tomás meanwhile, at the desk.
        mockMvc.perform(post("/api/patients").with(RECEPTION).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fullName": "Tomás Pérez", "documentType": "DNI", "documentNumber": "%s",
                                 "documentSeen": true}""".formatted(tomas)))
                .andExpect(status().isCreated());

        confirm(lastRegistrationTokenTo(email)).andExpect(status().isOk())
                .andExpect(jsonPath("$.notRegistered[0]").value("Tomás Pérez"));
        assertThat(patientsOf(tomas)).hasSize(1);
        // With no child registered, no group is formed.
        assertThat(groupsOf(idOf(adult))).isEmpty();
    }

    @Test
    void whatTheFormAloneCanTellOfTheChildrenIsSaidAndNothingIsKept() throws Exception {
        String email = address();
        String adult = dni();
        // 13 on 10 March 2026 is born by 10 March 2013.
        register(registration(dni(), email, child("Tomás", dni(), "2013-03-10", "MOTHER")))
                .andExpect(status().isConflict());
        register(registration(dni(), address(), child("Tomás", dni(), "2013-03-11", "MOTHER")))
                .andExpect(status().isAccepted());
        String twice = dni();
        register(registration(dni(), email, child("Tomás", twice, "2018-01-01", "MOTHER"),
                child("Luz", twice, "2019-01-01", "MOTHER"))).andExpect(status().isBadRequest());
        register(registration(adult, email, child("Tomás", adult, "2018-01-01", "MOTHER")))
                .andExpect(status().isBadRequest());
        String[] six = new String[6];
        for (int i = 0; i < six.length; i++) {
            six[i] = child("Hijo", dni(), "2018-01-01", "MOTHER");
        }
        register(registration(dni(), email, six)).andExpect(status().isBadRequest());
        register(registration(dni(), email, child("Tomás", "12AB", "2018-01-01", "MOTHER")))
                .andExpect(status().isBadRequest());
        register(registration(dni(), email, """
                {"fullName": "Tomás", "documentType": "DNI", "documentNumber": "%s", "birthDate": "2018-01-01"}"""
                .formatted(dni()))).andExpect(status().isBadRequest());
        register(registration(dni(), email, """
                {"fullName": "Tomás", "documentType": "DNI", "documentNumber": "%s", "relation": "MOTHER"}"""
                .formatted(dni()))).andExpect(status().isBadRequest());

        assertThat(jdbc.queryForObject("select count(*) from web_registration where email = ?", Integer.class, email))
                .isZero();
    }

    /** A child of 13 is refused for a known adult as for any other, before any document is looked at. */
    @Test
    void aKnownAdultsChildrenAreNotKeptEither() throws Exception {
        Patient knownAdult = accounts.adult();
        String email = address();
        register(registration(knownAdult.dni(), email, child("Tomás", dni(), "2018-01-01", "MOTHER")))
                .andExpect(status().isAccepted());
        assertThat(KeptAccountMail.REGISTRATIONS_REFUSED.stream().filter(sent -> sent.address().equals(email)))
                .hasSize(1);
        assertThat(jdbc.queryForObject("select count(*) from web_registration where email = ?", Integer.class, email))
                .isZero();
        register(registration(knownAdult.dni(), address(), child("Tomás", dni(), "2013-01-01", "MOTHER")))
                .andExpect(status().isConflict());
    }

    /** Only a child added at someone's request has a relation to prove (V44). */
    @Test
    void whoeverJoinedThemselvesHasNoRelationToProve() throws Exception {
        List<String> family = registeredWithTwoChildren();
        String groupId = groupsOf(idOf(family.get(0))).get(0).get("id").asText();
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> jdbc.update("""
                insert into family_member (group_id, patient_id, shares, added_at, relation_proved)
                values (?, ?, true, ?, false)""", UUID.fromString(groupId), UUID.randomUUID(),
                LocalDateTime.of(2026, 3, 10, 0, 0)))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    }

    @Test
    void theChildrenOfARegistrationNeverConfirmedAreForgottenWithIt() throws Exception {
        String email = address();
        register(registration(dni(), email, child("Tomás", dni(), "2018-01-01", "MOTHER")))
                .andExpect(status().isAccepted());
        jdbc.update("update web_registration set expires_at = ? where email = ?", LocalDateTime.of(2026, 3, 9, 0, 0),
                email);
        assertThat(registrations.forgetExpired()).isPositive();
        assertThat(jdbc.queryForObject("""
                select count(*) from web_registration_child c
                where not exists (select 1 from web_registration r where r.id = c.registration_id)""", Integer.class))
                .isZero();
        assertThat(jdbc.queryForObject("select count(*) from web_registration where email = ?", Integer.class, email))
                .isZero();
    }

    /**
     * The adult books, sees and cancels for each child, one ahead each until reception verifies them; a partner in
     * the group acts for them only once reception proves the relation.
     */
    @Test
    void theAdultActsForEachChildOneAheadUntilReceptionVerifiesThemAndProvesTheRelation() throws Exception {
        CreatedPractitioner arias = practitionerBookedOnTheWeb();
        List<String> family = registeredWithTwoChildren();
        String adult = family.get(0);
        String tomasId = idOf(family.get(1));
        String sofiaId = idOf(family.get(2));
        String groupId = groupsOf(idOf(adult)).get(0).get("id").asText();

        String booked = read(book(arias, adult, tomasId, "2026-03-16T10:00:00").andExpect(status().isCreated()))
                .get("id").asText();
        book(arias, adult, tomasId, "2026-03-17T10:00:00").andExpect(status().isConflict());
        book(arias, adult, sofiaId, "2026-03-17T10:00:00").andExpect(status().isCreated());
        mockMvc.perform(get("/api/portal/appointments").with(as(adult)))
                .andExpect(jsonPath("$.family[?(@.patientId == '%s')].upcoming[0].startsAt", tomasId)
                        .value(hasItem("2026-03-16T10:00:00")));
        mockMvc.perform(post("/api/portal/appointments/" + booked + "/cancel").with(as(adult)))
                .andExpect(status().isNoContent());
        book(arias, adult, tomasId, "2026-03-18T10:00:00").andExpect(status().isCreated());

        // A partner with an account joins the group at the desk: the children's relation is not proved yet.
        Patient partner = accounts.withAccount("Luis Pérez", "1988-01-01");
        mockMvc.perform(post("/api/family-groups/" + groupId + "/members").with(RECEPTION)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"patientId\": \"%s\", \"shares\": true}".formatted(partner.id())))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/portal/booking/options").with(httpBasic("DNI " + partner.dni(), PASSWORD)))
                .andExpect(jsonPath("$.patients[*].id").value(not(hasItem(tomasId))));

        String proof = "/api/family-groups/" + groupId + "/members/" + tomasId + "/relation-proof";
        mockMvc.perform(post(proof).with(RECEPTION).contentType(MediaType.APPLICATION_JSON)
                .content("{\"proofSeen\": false}")).andExpect(status().isConflict());
        mockMvc.perform(post(proof).with(as(adult)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"proofSeen\": true}")).andExpect(status().isUnauthorized());
        JsonNode proved = read(mockMvc.perform(post(proof).with(RECEPTION).contentType(MediaType.APPLICATION_JSON)
                .content("{\"proofSeen\": true}")).andExpect(status().isOk()));
        assertThat(memberOf(proved, tomasId).get("relationProved").asBoolean()).isTrue();
        assertThat(memberOf(proved, sofiaId).get("relationProved").asBoolean()).isFalse();
        mockMvc.perform(post(proof).with(RECEPTION).contentType(MediaType.APPLICATION_JSON)
                .content("{\"proofSeen\": true}")).andExpect(status().isConflict());
        JsonNode history = read(mockMvc.perform(get("/api/family-groups/" + groupId + "/changes").with(RECEPTION)));
        // The tests' clock stands still, so the history's order says nothing here: the entry is looked up.
        JsonNode provedEntry = StreamSupport.stream(history.spliterator(), false)
                .filter(entry -> entry.get("action").asText().equals("RELATION_PROVED"))
                .reduce((first, second) -> {
                    throw new AssertionError("The relation was proved once");
                })
                .orElseThrow();
        assertThat(provedEntry.get("patientId").asText()).isEqualTo(tomasId);
        assertThat(provedEntry.get("by").asText()).isEqualTo("recepcion");
        mockMvc.perform(get("/api/portal/booking/options").with(httpBasic("DNI " + partner.dni(), PASSWORD)))
                .andExpect(jsonPath("$.patients[*].id").value(hasItem(tomasId)))
                .andExpect(jsonPath("$.patients[*].id").value(not(hasItem(sofiaId))));

        // Still one ahead until reception sees Tomás's document; then the usual three.
        book(arias, adult, tomasId, "2026-03-19T10:00:00").andExpect(status().isConflict());
        mockMvc.perform(put("/api/patients/" + tomasId).with(RECEPTION).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fullName": "Tomás Pérez", "documentType": "DNI", "documentNumber": "%s",
                                 "documentSeen": true}""".formatted(family.get(1))))
                .andExpect(jsonPath("$.identity").value("VERIFIED"));
        book(arias, adult, tomasId, "2026-03-19T10:00:00").andExpect(status().isCreated());
        book(arias, adult, tomasId, "2026-03-20T10:00:00").andExpect(status().isCreated());
    }

    private ResultActions book(CreatedPractitioner practitioner, String adultDni, String patientId, String startsAt)
            throws Exception {
        return mockMvc.perform(post("/api/portal/appointments").with(as(adultDni))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"patientId\": \"%s\", \"practitionerId\": \"%s\", \"reasonId\": \"%s\", \"startsAt\": \"%s\"}"
                        .formatted(patientId, practitioner.id(), practitioner.consulta(), startsAt)));
    }

    /** One who works every day, whose "consulta" is booked on the web. */
    private CreatedPractitioner practitionerBookedOnTheWeb() throws Exception {
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
                .andExpect(status().isOk());
        return practitioner;
    }

    private ResultActions registerChild(String adultDni, String body) throws Exception {
        return mockMvc.perform(post("/api/portal/family/children").with(as(adultDni))
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private static String accountChild(String name, String dni, String birthDate, String groupId) {
        return """
                {"fullName": "%s", "documentType": "DNI", "documentNumber": "%s", "birthDate": "%s",
                 "relation": "FATHER", "groupId": %s}""".formatted(name, dni, birthDate,
                groupId == null ? "null" : "\"" + groupId + "\"");
    }

    /** Later, from the account: at once, into a group of the adult's or a new one, five a day at most (D-92). */
    @Test
    void anAdultRegistersAChildFromTheirAccountFiveADayAtMost() throws Exception {
        Patient adult = accounts.withAccount("Luis Gómez", "1985-01-01");
        String account = accounts.accountOf(adult);
        String first = dni();
        registerChild(adult.dni(), accountChild("Mía Gómez", first, "2019-05-05", null))
                .andExpect(status().isAccepted()).andExpect(content().string(""));
        String miaId = idOf(first);
        JsonNode group = groupsOf(adult.id()).get(0);
        assertThat(group.get("name").asText()).isEqualTo("Familia de Luis Gómez");
        assertThat(memberOf(group, miaId).get("relation").asText()).isEqualTo("FATHER");
        assertThat(memberOf(group, miaId).get("relationProved").asBoolean()).isFalse();
        assertThat(patientsOf(first).get(0).get("registeredVia").asText()).isEqualTo("WEB");
        String groupId = group.get("id").asText();

        String second = dni();
        registerChild(adult.dni(), accountChild("Leo Gómez", second, "2021-05-05", groupId))
                .andExpect(status().isAccepted());
        assertThat(groupsOf(adult.id())).hasSize(1);
        assertThat(memberOf(groupsOf(adult.id()).get(0), idOf(second)).get("requestedBy").asText())
                .isEqualTo(adult.id());

        // A document the practice knows: the same answer, and nothing registered.
        Patient known = accounts.patient("Juan Pérez", false, "2017-02-02");
        registerChild(adult.dni(), accountChild("Juan Pérez", known.dni(), "2017-02-02", groupId))
                .andExpect(status().isAccepted()).andExpect(content().string(""));
        assertThat(groupsOf(known.id())).isEmpty();

        // What the form tells is said, and not counted.
        registerChild(adult.dni(), accountChild("Ana Gómez", dni(), "2013-03-10", groupId))
                .andExpect(status().isConflict());
        registerChild(adult.dni(), accountChild("Ana Gómez", "12AB", "2019-01-01", groupId))
                .andExpect(status().isBadRequest());
        Patient stranger = accounts.withAccount("Otro Adulto", "1980-01-01");
        registerChild(stranger.dni(), accountChild("Ana Gómez", dni(), "2019-01-01", groupId))
                .andExpect(status().isConflict());

        registerChild(adult.dni(), accountChild("Ana Gómez", dni(), "2019-01-01", groupId))
                .andExpect(status().isAccepted());
        registerChild(adult.dni(), accountChild("Eva Gómez", dni(), "2019-01-01", groupId))
                .andExpect(status().isAccepted());
        String sixth = dni();
        registerChild(adult.dni(), accountChild("Ema Gómez", sixth, "2019-01-01", groupId))
                .andExpect(status().isTooManyRequests());
        assertThat(patientsOf(sixth)).isEmpty();
        JsonNode changes = read(mockMvc.perform(get("/api/patient-accounts/" + account + "/changes")
                .with(RECEPTION)));
        assertThat(changes.findValuesAsText("action").stream().filter("CHILD_REQUESTED"::equals)).hasSize(5);
        // A day later, the count starts again.
        jdbc.update("update patient_account_change set changed_at = ? where account_id = ? and action = ?",
                LocalDateTime.of(2026, 3, 8, 23, 0), UUID.fromString(account), "CHILD_REQUESTED");
        registerChild(adult.dni(), accountChild("Ema Gómez", sixth, "2019-01-01", groupId))
                .andExpect(status().isAccepted());
        assertThat(patientsOf(sixth)).hasSize(1);
    }

    @Test
    void onlyAnAdultRegistersAChildFromTheirAccount() throws Exception {
        Patient teen = accounts.withAccount("Ana Gómez", "2010-01-01");
        registerChild(teen.dni(), accountChild("Mía Gómez", dni(), "2019-05-05", null))
                .andExpect(status().isConflict());
        registerChild(teen.dni(), accountChild("Mía Gómez", dni(), "2019-05-05", UUID.randomUUID().toString()))
                .andExpect(status().isConflict());
        mockMvc.perform(post("/api/portal/family/children").contentType(MediaType.APPLICATION_JSON)
                .content(accountChild("Mía Gómez", dni(), "2019-05-05", null))).andExpect(status().isUnauthorized());
    }
}
