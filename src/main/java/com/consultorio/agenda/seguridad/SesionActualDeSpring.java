package com.consultorio.agenda.seguridad;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import com.consultorio.agenda.seguridad.api.SesionActual;
import com.consultorio.agenda.seguridad.api.UsuarioAutenticado;

@Component
class SesionActualDeSpring implements SesionActual {

    @Override
    public UsuarioAutenticado usuario() {
        Authentication autenticacion = SecurityContextHolder.getContext().getAuthentication();
        if (autenticacion == null || !(autenticacion.getPrincipal() instanceof UsuarioDetalles detalles)) {
            throw new IllegalStateException("No hay un usuario autenticado en esta peticion");
        }
        return detalles.usuarioAutenticado();
    }
}
