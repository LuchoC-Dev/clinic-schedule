package com.consultorio.agenda.seguridad;

import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class UsuarioDetallesServicio implements UserDetailsService {

    private final UsuarioRepositorio usuarios;

    UsuarioDetallesServicio(UsuarioRepositorio usuarios) {
        this.usuarios = usuarios;
    }

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String usuario) {
        return usuarios.findByUsuarioAndActivoTrue(usuario)
                .map(UsuarioDetalles::new)
                .orElseThrow(() -> new UsernameNotFoundException("Usuario inexistente o inactivo"));
    }
}
