package com.consultorio.agenda.compartido.web;

import java.time.OffsetDateTime;
import java.util.Map;

/**
 * Cuerpo de error uniforme para toda la API. {@code campos} solo viene cuando el error
 * es de validacion; en los demas casos es null y no se serializa.
 */
public record ErrorRespuesta(
        OffsetDateTime momento,
        int estado,
        String error,
        String mensaje,
        Map<String, String> campos) {

    static ErrorRespuesta de(int estado, String error, String mensaje) {
        return new ErrorRespuesta(OffsetDateTime.now(), estado, error, mensaje, null);
    }

    static ErrorRespuesta deValidacion(String mensaje, Map<String, String> campos) {
        return new ErrorRespuesta(OffsetDateTime.now(), 400, "Solicitud invalida", mensaje, campos);
    }
}
