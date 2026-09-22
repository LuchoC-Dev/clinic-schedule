package com.consultorio.agenda.seguridad;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.consultorio.agenda.seguridad.api.SesionActual;
import com.consultorio.agenda.seguridad.api.UsuarioAutenticado;

/**
 * Quien soy y que puedo hacer. Lo consume la interfaz para decidir que mostrar, y sirve
 * para verificar el login sin depender de ninguna otra funcionalidad.
 */
@RestController
@RequestMapping("/api/sesion")
class SesionControlador {

    private final SesionActual sesionActual;

    SesionControlador(SesionActual sesionActual) {
        this.sesionActual = sesionActual;
    }

    @GetMapping
    UsuarioAutenticado actual() {
        return sesionActual.usuario();
    }
}
