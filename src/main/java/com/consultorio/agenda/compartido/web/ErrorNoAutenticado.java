package com.consultorio.agenda.compartido.web;

import java.io.IOException;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * El 401 lo produce la cadena de filtros, antes de que intervenga
 * {@link ManejadorDeErrores}. Este punto de entrada hace que tambien tenga el cuerpo de
 * error uniforme, y evita el dialogo de credenciales del navegador al no enviar
 * {@code WWW-Authenticate}.
 */
@Component
class ErrorNoAutenticado implements AuthenticationEntryPoint {

    private final ObjectMapper json;

    ErrorNoAutenticado(ObjectMapper json) {
        this.json = json;
    }

    @Override
    public void commence(HttpServletRequest peticion, HttpServletResponse respuesta,
            AuthenticationException excepcion) throws IOException {
        respuesta.setStatus(HttpStatus.UNAUTHORIZED.value());
        respuesta.setContentType(MediaType.APPLICATION_JSON_VALUE);
        respuesta.setCharacterEncoding("UTF-8");
        json.writeValue(respuesta.getOutputStream(),
                ErrorRespuesta.de(401, "No autenticado", "Se requieren credenciales validas"));
    }
}
