package com.consultorio.agenda.seguridad;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class SesionApiTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void sinCredencialesLaApiResponde401() throws Exception {
        mockMvc.perform(get("/api/sesion"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void conCredencialesIncorrectasLaApiResponde401() throws Exception {
        mockMvc.perform(get("/api/sesion").with(httpBasic("recepcion", "incorrecta")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void recepcionSeIdentificaSinProfesionalAsociado() throws Exception {
        mockMvc.perform(get("/api/sesion").with(httpBasic("recepcion", "recepcion123")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.usuario").value("recepcion"))
                .andExpect(jsonPath("$.rol").value("RECEPCION"))
                .andExpect(jsonPath("$.profesionalId").doesNotExist());
    }

    @Test
    void elProfesionalSeIdentificaConElProfesionalQueRepresenta() throws Exception {
        mockMvc.perform(get("/api/sesion").with(httpBasic("arias", "profesional123")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.usuario").value("arias"))
                .andExpect(jsonPath("$.rol").value("PROFESIONAL"))
                .andExpect(jsonPath("$.profesionalId").value(1));
    }

    @Test
    void losErroresDeLaApiUsanElMismoCuerpo() throws Exception {
        mockMvc.perform(get("/api/sesion"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.estado").value(401))
                .andExpect(jsonPath("$.error").value("No autenticado"));

        mockMvc.perform(get("/api/inexistente").with(httpBasic("recepcion", "recepcion123")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.estado").value(404))
                .andExpect(jsonPath("$.error").value("No encontrado"));
    }

    @Test
    void elFormularioDeLoginEsPublico() throws Exception {
        mockMvc.perform(get("/login"))
                .andExpect(status().isOk());
    }
}
