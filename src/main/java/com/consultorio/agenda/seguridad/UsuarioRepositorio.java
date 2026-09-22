package com.consultorio.agenda.seguridad;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

interface UsuarioRepositorio extends JpaRepository<Usuario, Long> {

    Optional<Usuario> findByUsuarioAndActivoTrue(String usuario);
}
