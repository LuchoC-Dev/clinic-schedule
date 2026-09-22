package com.consultorio.agenda.seguridad;

import java.util.Collection;
import java.util.List;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import com.consultorio.agenda.seguridad.api.UsuarioAutenticado;

/**
 * Principal de Spring Security que carga tambien el dato que necesita la aplicacion:
 * a que profesional representa el usuario.
 */
class UsuarioDetalles implements UserDetails {

    private final UsuarioAutenticado usuarioAutenticado;
    private final String claveHash;

    UsuarioDetalles(Usuario usuario) {
        this.usuarioAutenticado = new UsuarioAutenticado(
                usuario.getId(), usuario.getUsuario(), usuario.getRol(), usuario.getProfesionalId());
        this.claveHash = usuario.getClaveHash();
    }

    UsuarioAutenticado usuarioAutenticado() {
        return usuarioAutenticado;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority(usuarioAutenticado.rol().autoridad()));
    }

    @Override
    public String getPassword() {
        return claveHash;
    }

    @Override
    public String getUsername() {
        return usuarioAutenticado.usuario();
    }
}
