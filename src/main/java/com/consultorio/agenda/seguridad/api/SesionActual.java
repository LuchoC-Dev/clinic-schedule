package com.consultorio.agenda.seguridad.api;

/**
 * Puerta de entrada del modulo de seguridad para el resto de la aplicacion (D-05).
 * Los otros modulos preguntan por aca quien esta autenticado, sin conocer Spring Security
 * ni la entidad Usuario.
 */
public interface SesionActual {

    /** @throws IllegalStateException si se invoca fuera de una peticion autenticada. */
    UsuarioAutenticado usuario();
}
