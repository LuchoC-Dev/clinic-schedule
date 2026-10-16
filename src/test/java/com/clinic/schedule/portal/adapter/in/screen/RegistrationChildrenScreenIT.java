package com.clinic.schedule.portal.adapter.in.screen;

import static com.clinic.schedule.portal.PatientAccountFixture.PASSWORD;
import static com.clinic.schedule.portal.PatientAccountFixture.RECEPTION;
import static com.clinic.schedule.portal.PatientAccountFixture.address;
import static com.clinic.schedule.portal.PatientAccountFixture.dni;
import static com.clinic.schedule.portal.PatientAccountFixture.lastRegistrationTokenTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.clinic.schedule.portal.KeptAccountMail;
import com.clinic.schedule.portal.PatientAccountFixture;
import com.clinic.schedule.portal.PatientAccountFixture.Patient;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Registering children under 13 on the screens (D-92), in Spanish: with the adult's registration, in the form's
 * blocks; later from *Mi familia*; the login naming a child it could not register; and reception reading the relation
 * not proved on the group's screen, and proving it there.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(KeptAccountMail.class)
class RegistrationChildrenScreenIT {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper json;

    private PatientAccountFixture accounts;

    @BeforeEach
    void setUp() {
        accounts = new PatientAccountFixture(mockMvc, json);
    }

    /** The adult's form, with a block for each child given as {name, dni, birth date, relation}. */
    private ResultActions register(String dni, String email, List<String[]> children) throws Exception {
        MockHttpServletRequestBuilder request = post("/portal/register").with(csrf()).param("fullName", "Ana Pérez")
                .param("documentType", "DNI").param("documentNumber", dni).param("birthDate", "1990-05-14")
                .param("phone", "").param("email", email).param("password", PASSWORD)
                .param("repeatedPassword", PASSWORD);
        for (int i = 0; i < RegistrationViewController.CHILD_SLOTS; i++) {
            String[] child = i < children.size() ? children.get(i) : new String[] {"", "", "", "MOTHER"};
            request = request.param("childFullName", child[0]).param("childDocumentType", "DNI")
                    .param("childDocumentNumber", child[1]).param("childBirthDate", child[2])
                    .param("childRelation", child[3]);
        }
        return mockMvc.perform(request);
    }

    private JsonNode read(ResultActions actions) throws Exception {
        return json.readTree(actions.andReturn().getResponse().getContentAsString());
    }

    private String idOf(String dni) throws Exception {
        return read(mockMvc.perform(get("/api/patients").with(RECEPTION).param("query", dni))).get(0).get("id")
                .asText();
    }

    private MockHttpSession logIn(String dni) throws Exception {
        return (MockHttpSession) mockMvc.perform(post("/portal/login").with(csrf()).param("documentType", "DNI")
                        .param("documentNumber", dni).param("password", PASSWORD))
                .andExpect(redirectedUrl("/portal/appointments"))
                .andReturn().getRequest().getSession(false);
    }

    @Test
    void anAdultRegistersWithTheirChildrenAndReceptionProvesTheRelation() throws Exception {
        mockMvc.perform(get("/portal/register"))
                .andExpect(content().string(allOf(containsString("Tus hijos menores de 13, si querés"),
                        containsString(">Hijo 3<"), containsString("name=\"childRelation\""))));

        String adult = dni();
        String tomas = dni();
        String luz = dni();
        String email = address();
        register(adult, email, List.of(new String[] {"Tomás Pérez", tomas, "2018-04-02", "MOTHER"},
                new String[] {"Luz Pérez", luz, "2019-04-02", "MOTHER"}))
                .andExpect(redirectedUrl("/portal/register/sent"));
        // Luz is registered at reception before the email's link is followed.
        mockMvc.perform(post("/api/patients").with(RECEPTION).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fullName": "Luz Pérez", "documentType": "DNI", "documentNumber": "%s",
                                 "documentSeen": true}""".formatted(luz)))
                .andExpect(status().isCreated());
        // Confirmed in the patients' application, through the API (D-110).
        mockMvc.perform(post("/api/portal/registrations/confirmation").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\": \"%s\"}".formatted(lastRegistrationTokenTo(email))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.notRegistered[0]").value("Luz Pérez"));

        MockHttpSession session = logIn(adult);
        mockMvc.perform(get("/portal/family").session(session))
                .andExpect(content().string(allOf(containsString("Familia de Ana Pérez"),
                        containsString("Tomás Pérez"), containsString("Registrar a un hijo menor de 13"))));

        String adultId = idOf(adult);
        String tomasId = idOf(tomas);
        String groupId = read(mockMvc.perform(get("/api/family-groups").with(RECEPTION).param("patientId", adultId)))
                .get(0).get("id").asText();
        mockMvc.perform(get("/family-groups/" + groupId).with(RECEPTION))
                .andExpect(content().string(allOf(containsString("A pedido de Ana Pérez (madre)"),
                        containsString("Vínculo sin probar · pedido desde la web"),
                        containsString(">Probar vínculo<"), containsString("Vi lo que prueba el vínculo"))));
        mockMvc.perform(post("/family-groups/" + groupId + "/members/" + tomasId + "/relation-proof")
                        .with(RECEPTION).with(csrf()))
                .andExpect(flash().attribute("error", "Para probar el vínculo, mirá la documentación que lo acredita y "
                        + "marcalo."));
        mockMvc.perform(post("/family-groups/" + groupId + "/members/" + tomasId + "/relation-proof")
                        .with(RECEPTION).with(csrf()).param("proofSeen", "true"))
                .andExpect(redirectedUrl("/family-groups/" + groupId))
                .andExpect(flash().attribute("message", "Vínculo probado."));
        String screen = mockMvc.perform(get("/family-groups/" + groupId).with(RECEPTION))
                .andReturn().getResponse().getContentAsString();
        assertThat(screen).doesNotContain("Vínculo sin probar").doesNotContain(">Probar vínculo<")
                .contains("Vínculo probado — Tomás Pérez").contains("el paciente (web)");
        mockMvc.perform(post("/family-groups/" + groupId + "/members/" + tomasId + "/relation-proof")
                        .with(RECEPTION).with(csrf()).param("proofSeen", "true"))
                .andExpect(flash().attribute("error", "Ese miembro no tiene un vínculo por probar."));
        mockMvc.perform(get("/patients/" + tomasId).with(RECEPTION))
                .andExpect(content().string(allOf(containsString("No verificada"),
                        containsString("Registrado desde la web"))));
    }

    @Test
    void whatTheFormCanTellOfTheChildrenIsSaidAndTheirBlocksComeBack() throws Exception {
        String twice = dni();
        register(dni(), address(), List.of(new String[] {"Tomás Pérez", twice, "2018-04-02", "FATHER"},
                new String[] {"Luz Pérez", twice, "2019-04-02", "MOTHER"}))
                .andExpect(content().string(allOf(containsString("Pusiste el mismo documento dos veces."),
                        containsString("value=\"Tomás Pérez\""), containsString("value=\"2019-04-02\""),
                        containsString("<option value=\"FATHER\" selected=\"selected\">padre</option>"))));
        register(dni(), address(), List.<String[]>of(new String[] {"Tomás Pérez", dni(), "2013-03-10", "MOTHER"}))
                .andExpect(content().string(containsString("Desde la web se registran hijos menores de 13.")));
        register(dni(), address(), List.<String[]>of(new String[] {"Tomás Pérez", dni(), "", "MOTHER"}))
                .andExpect(content().string(containsString("Falta la fecha de nacimiento de uno de tus hijos.")));
        register(dni(), address(), List.<String[]>of(new String[] {"Tomás Pérez", "12AB", "2018-01-01", "MOTHER"}))
                .andExpect(content().string(containsString("Revisá los datos")));
    }

    @Test
    void anAdultRegistersAChildFromTheirFamilyAndAnAdolescentIsNotOffered() throws Exception {
        Patient adult = accounts.withAccount("Luis Gómez", "1985-01-01");
        MockHttpSession session = logIn(adult.dni());
        mockMvc.perform(get("/portal/family").session(session))
                .andExpect(content().string(allOf(containsString("Registrar a un hijo menor de 13"),
                        containsString(">Un grupo nuevo<"), containsString("Si registrás a un hijo desde acá"))));
        String mia = dni();
        mockMvc.perform(post("/portal/family/children").session(session).with(csrf()).param("fullName", "Mía Gómez")
                        .param("documentType", "DNI").param("documentNumber", mia).param("birthDate", "2019-05-05")
                        .param("relation", "FATHER").param("groupId", ""))
                .andExpect(redirectedUrl("/portal/family"))
                .andExpect(flash().attribute("message", "Recibimos el pedido. Si Mía Gómez no aparece en tu familia, "
                        + "acercate a recepción con su documento."));
        mockMvc.perform(get("/portal/family").session(session))
                .andExpect(content().string(allOf(containsString("Familia de Luis Gómez"), containsString("Mía Gómez"),
                        containsString("menor de 13"))));
        mockMvc.perform(post("/portal/family/children").session(session).with(csrf()).param("fullName", "Ana Gómez")
                        .param("documentType", "DNI").param("documentNumber", dni()).param("birthDate", "2010-05-05")
                        .param("relation", "FATHER"))
                .andExpect(flash().attribute("error", "Desde la web registrás a tus hijos menores de 13, en un grupo "
                        + "tuyo."));
        mockMvc.perform(get("/patient-accounts").with(RECEPTION).param("patient", adult.id()))
                .andExpect(content().string(containsString("Pidió registrar a un hijo desde la web")));

        Patient teen = accounts.withAccount("Ana Gómez", "2010-01-01");
        mockMvc.perform(get("/portal/family").session(logIn(teen.dni())))
                .andExpect(content().string(not(containsString("Registrar a un hijo menor de 13"))));
    }
}
