package com.clinic.schedule.portal.adapter.in.screen;

import static com.clinic.schedule.portal.PatientAccountFixture.RECEPTION;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Reception's screens of the family groups (D-84), in Spanish: forming a group at the desk, who shares their
 * appointments, why the group's rules refuse something, and its history. "Today" is 10 March 2026.
 */
@SpringBootTest
@AutoConfigureMockMvc
class FamilyGroupScreenIT {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper json;

    private String patient(String fullName, String birthDate) throws Exception {
        String body = """
                {"fullName": "%s", "documentType": "DNI", "documentNumber": "%d", "documentSeen": true,
                 "birthDate": %s}
                """.formatted(fullName, ThreadLocalRandom.current().nextInt(50_000_000, 99_999_999),
                birthDate == null ? "null" : "\"" + birthDate + "\"");
        return json.readTree(mockMvc.perform(post("/api/patients").with(RECEPTION)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString()).get("id").asText();
    }

    private String group(String name) throws Exception {
        String url = mockMvc.perform(post("/family-groups").with(RECEPTION).with(csrf()).param("name", name))
                .andExpect(flash().attribute("message", "Grupo creado. Agregá a sus miembros."))
                .andReturn().getResponse().getRedirectedUrl();
        assertThat(url).startsWith("/family-groups/");
        return url.substring("/family-groups/".length());
    }

    private ResultActions join(String group, String patient, boolean shares) throws Exception {
        return mockMvc.perform(post("/family-groups/" + group + "/members").with(RECEPTION).with(csrf())
                .param("patientId", patient).param("shares", String.valueOf(shares)));
    }

    private ResultActions addChild(String group, String child, String requester, String relation,
            boolean proofSeen) throws Exception {
        return mockMvc.perform(post("/family-groups/" + group + "/children").with(RECEPTION).with(csrf())
                .param("patientId", child).param("requestedBy", requester).param("relation", relation)
                .param("proofSeen", String.valueOf(proofSeen)));
    }

    private ResultActions share(String group, String patient, boolean shares) throws Exception {
        return mockMvc.perform(post("/family-groups/" + group + "/members/" + patient + "/sharing").with(RECEPTION)
                .with(csrf()).param("shares", String.valueOf(shares)));
    }

    private ResultActions remove(String group, String patient) throws Exception {
        return mockMvc.perform(post("/family-groups/" + group + "/members/" + patient + "/remove").with(RECEPTION)
                .with(csrf()));
    }

    private ResultActions screen(String group) throws Exception {
        return mockMvc.perform(get("/family-groups/" + group).with(RECEPTION)).andExpect(status().isOk());
    }

    @Test
    void receptionFormsAFamilyFromThePatientsScreen() throws Exception {
        String father = patient("Pablo Suárez", "1982-04-02");
        String son = patient("Bruno Suárez", "2010-09-09");
        String daughter = patient("Lola Suárez", "2017-12-01");
        mockMvc.perform(get("/patients/" + father).with(RECEPTION))
                .andExpect(content().string(containsString("/family-groups?patient=" + father)));
        mockMvc.perform(get("/family-groups").with(RECEPTION).param("patient", father))
                .andExpect(status().isOk())
                .andExpect(content().string(allOf(containsString(">Pablo Suárez<"),
                        containsString("No hay grupos."))));

        String group = group("Familia Suárez");
        screen(group).andExpect(content().string(allOf(containsString(">Familia Suárez<"),
                containsString("Todavía no tiene miembros."), containsString(">Grupo creado<"))));
        mockMvc.perform(get("/family-groups/" + group).with(RECEPTION).param("query", "Suárez"))
                .andExpect(content().string(allOf(containsString("Primero tiene que entrar un adulto."),
                        not(containsString("Agregar menor")))));

        join(group, father, false).andExpect(redirectedUrl("/family-groups/" + group))
                .andExpect(flash().attribute("message", "Entró al grupo."));
        join(group, son, false).andExpect(flash().attribute("message", "Entró al grupo."));
        addChild(group, daughter, father, "FATHER", true)
                .andExpect(redirectedUrl("/family-groups/" + group))
                .andExpect(flash().attribute("message", "Menor agregado al grupo."));

        screen(group).andExpect(content().string(allOf(
                containsString(">adulto<"), containsString(">de 13 a 17<"), containsString(">menor de 13<"),
                containsString(">Comparte sus turnos<"), containsString(">Turnos privados<"),
                containsString(">Siempre (menor de 13)<"), containsString(">A pedido de Pablo Suárez (padre)<"),
                containsString(">Entró — Pablo Suárez<"), containsString(">Agregado como menor — Lola Suárez<"),
                containsString(">recepcion<"))));
        String page = screen(group).andReturn().getResponse().getContentAsString();
        // The button offers the opposite of what the member does now, and a child has none.
        assertThat(page).containsPattern("members/" + father + "/sharing\"><input[^>]*/>\\s*"
                + "<input type=\"hidden\" name=\"shares\" value=\"false\">\\s*"
                + "<button type=\"submit\">Dejar de compartir<");
        assertThat(page).containsPattern("members/" + son + "/sharing\"><input[^>]*/>\\s*"
                + "<input type=\"hidden\" name=\"shares\" value=\"true\">\\s*<button type=\"submit\">Compartir<");
        assertThat(page).doesNotContain("members/" + daughter + "/sharing");
        mockMvc.perform(get("/family-groups/" + group).with(RECEPTION).param("query", "Suárez"))
                .andExpect(content().string(allOf(containsString("Agregar menor"),
                        containsString("value=\"" + father + "\">Pablo Suárez<"),
                        not(containsString("value=\"" + son + "\">Bruno Suárez<")))));

        share(group, son, true).andExpect(flash().attribute("message", "Ahora comparte sus turnos con el grupo."));
        share(group, father, false)
                .andExpect(flash().attribute("message", "Sus turnos ya no se comparten con el grupo."));
        remove(group, son).andExpect(flash().attribute("message", "Salió del grupo."));
        screen(group).andExpect(content().string(allOf(
                containsString(">Empezó a compartir sus turnos — Bruno Suárez<"),
                containsString(">Dejó de compartir sus turnos — Pablo Suárez<"),
                containsString(">Salió — Bruno Suárez<"), containsString(">Compartir<"),
                not(containsString(">Comparte sus turnos<")))));
        mockMvc.perform(get("/family-groups").with(RECEPTION).param("patient", daughter))
                .andExpect(content().string(allOf(containsString(">Familia Suárez<"), containsString(">2<"))));
        mockMvc.perform(get("/family-groups").with(RECEPTION))
                .andExpect(content().string(allOf(containsString("<h1>Grupos familiares</h1>"),
                        containsString(">Familia Suárez<"))));
        mockMvc.perform(get("/family-groups/" + group).with(RECEPTION).param("query", "nadie-se-llama-asi"))
                .andExpect(content().string(containsString("Ningún paciente coincide.")));

        // A date of birth taken away later leaves the member without an age to read.
        mockMvc.perform(put("/api/patients/" + father + "/details").with(RECEPTION)
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isOk());
        screen(group).andExpect(content().string(allOf(containsString(">sin fecha de nacimiento<"),
                not(containsString("members/" + father + "/sharing")))));
    }

    @Test
    void receptionIsToldWhyTheGroupRefusesIt() throws Exception {
        String mother = patient("Clara Vega", "1979-01-15");
        String teen = patient("Iván Vega", "2012-03-10");
        String child = patient("Mía Vega", "2013-03-11");
        String noBirthDate = patient("Sin Fecha", null);
        String group = group("Familia Vega");
        join(group, mother, false);
        join(group, teen, false);

        String[][] refusals = {
                {"join", noBirthDate, "Para entrar a un grupo, el paciente necesita su fecha de nacimiento cargada."},
                {"join", child, "Es menor de 13: se agrega como menor, a pedido de su madre, padre o tutor del grupo."},
                {"join", mother, "Ya está en el grupo."},
                {"child", teen, "Tiene 13 años o más: entra por sí mismo, presente y aceptando."},
                {"by-teen", child, "Un menor se agrega a pedido de un adulto del grupo."},
                {"no-proof", child,
                        "Para agregar un menor, mirá la documentación que acredita el vínculo y marcalo."},
                {"remove", noBirthDate, "No está en el grupo."}};
        for (String[] refusal : refusals) {
            ResultActions result = switch (refusal[0]) {
                case "join" -> join(group, refusal[1], false);
                case "child" -> addChild(group, refusal[1], mother, "MOTHER", true);
                case "by-teen" -> addChild(group, refusal[1], teen, "GUARDIAN", true);
                case "no-proof" -> addChild(group, refusal[1], mother, "MOTHER", false);
                default -> remove(group, refusal[1]);
            };
            result.andExpect(redirectedUrl("/family-groups/" + group))
                    .andExpect(flash().attribute("error", refusal[2]));
        }
        addChild(group, child, mother, "MOTHER", true)
                .andExpect(flash().attribute("message", "Menor agregado al grupo."));
        addChild(group, patient("Leo Vega", "2020-01-01"), mother, "GUARDIAN", true)
                .andExpect(flash().attribute("message", "Menor agregado al grupo."));
        share(group, child, false)
                .andExpect(flash().attribute("error", "Un menor de 13 comparte siempre sus turnos con su familia."));
        screen(group).andExpect(content().string(allOf(containsString(">A pedido de Clara Vega (madre)<"),
                containsString(">A pedido de Clara Vega (tutor o tutora)<"))));

        mockMvc.perform(post("/family-groups").with(RECEPTION).with(csrf()).param("name", " "))
                .andExpect(redirectedUrl("/family-groups"))
                .andExpect(flash().attribute("error", "El grupo necesita un nombre de hasta 100 caracteres."));
        mockMvc.perform(get("/family-groups/" + UUID.randomUUID()).with(RECEPTION)).andExpect(status().isNotFound());
        join(UUID.randomUUID().toString(), mother, false).andExpect(status().isNotFound());
    }
}
