package com.consultorio.agenda.compartido;

/**
 * Una regla del negocio impide completar la operacion: turnos superpuestos, un DNI ya
 * registrado, un horario fuera de la disponibilidad del profesional. Se traduce a 409.
 */
public class ReglaDeNegocioException extends RuntimeException {

    public ReglaDeNegocioException(String mensaje) {
        super(mensaje);
    }
}
