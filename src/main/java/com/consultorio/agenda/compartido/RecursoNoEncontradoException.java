package com.consultorio.agenda.compartido;

/** El recurso referenciado no existe. Se traduce a 404. */
public class RecursoNoEncontradoException extends RuntimeException {

    public RecursoNoEncontradoException(String mensaje) {
        super(mensaje);
    }
}
