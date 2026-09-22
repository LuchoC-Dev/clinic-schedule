package com.consultorio.agenda.seguridad.api;

/**
 * Quien esta usando la aplicacion. {@code profesionalId} solo tiene valor cuando el rol
 * es {@link Rol#PROFESIONAL}: un usuario de recepcion no representa a ningun profesional.
 */
public record UsuarioAutenticado(Long id, String usuario, Rol rol, Long profesionalId) {

    public boolean esRecepcion() {
        return rol == Rol.RECEPCION;
    }
}
