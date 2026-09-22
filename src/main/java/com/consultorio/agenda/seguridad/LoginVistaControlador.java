package com.consultorio.agenda.seguridad;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Unica vista de esta etapa: el formulario de login que necesita la cadena de filtros de
 * las vistas. Las pantallas de agenda llegan en la etapa 7.
 */
@Controller
class LoginVistaControlador {

    @GetMapping("/login")
    String login() {
        return "login";
    }
}
