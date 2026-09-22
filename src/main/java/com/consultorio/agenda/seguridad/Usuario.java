package com.consultorio.agenda.seguridad;

import com.consultorio.agenda.seguridad.api.Rol;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "usuario")
class Usuario {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String usuario;

    @Column(name = "clave_hash", nullable = false)
    private String claveHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Rol rol;

    /**
     * Profesional que representa este usuario. Nulo para recepcion. La clave foranea
     * hacia la tabla de profesionales se agrega en la etapa 3, cuando esa tabla exista.
     */
    @Column(name = "profesional_id")
    private Long profesionalId;

    @Column(nullable = false)
    private boolean activo;

    protected Usuario() {
        // requerido por JPA
    }

    Long getId() {
        return id;
    }

    String getUsuario() {
        return usuario;
    }

    String getClaveHash() {
        return claveHash;
    }

    Rol getRol() {
        return rol;
    }

    Long getProfesionalId() {
        return profesionalId;
    }

    boolean isActivo() {
        return activo;
    }
}
