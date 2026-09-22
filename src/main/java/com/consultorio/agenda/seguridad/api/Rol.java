package com.consultorio.agenda.seguridad.api;

/**
 * Roles de la version 1. Recepcion administra todo; el profesional solo consulta lo propio.
 */
public enum Rol {
    RECEPCION,
    PROFESIONAL;

    public String autoridad() {
        return "ROLE_" + name();
    }
}
