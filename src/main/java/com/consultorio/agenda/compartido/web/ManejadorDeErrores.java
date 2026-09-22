package com.consultorio.agenda.compartido.web;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import com.consultorio.agenda.compartido.RecursoNoEncontradoException;
import com.consultorio.agenda.compartido.ReglaDeNegocioException;

/** Traduce las excepciones de la aplicacion al cuerpo de error uniforme de la API. */
@RestControllerAdvice
class ManejadorDeErrores {

    @ExceptionHandler(RecursoNoEncontradoException.class)
    ResponseEntity<ErrorRespuesta> noEncontrado(RecursoNoEncontradoException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ErrorRespuesta.de(404, "No encontrado", e.getMessage()));
    }

    @ExceptionHandler(ReglaDeNegocioException.class)
    ResponseEntity<ErrorRespuesta> reglaDeNegocio(ReglaDeNegocioException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ErrorRespuesta.de(409, "Conflicto", e.getMessage()));
    }

    @ExceptionHandler(AccessDeniedException.class)
    ResponseEntity<ErrorRespuesta> accesoDenegado(AccessDeniedException e) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ErrorRespuesta.de(403, "Acceso denegado", "No tiene permiso para esta operacion"));
    }

    /** Ruta inexistente: se responde con el mismo cuerpo que el resto de la API. */
    @ExceptionHandler(NoResourceFoundException.class)
    ResponseEntity<ErrorRespuesta> rutaInexistente(NoResourceFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ErrorRespuesta.de(404, "No encontrado", "La ruta solicitada no existe"));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ErrorRespuesta> validacion(MethodArgumentNotValidException e) {
        Map<String, String> campos = new LinkedHashMap<>();
        for (FieldError error : e.getBindingResult().getFieldErrors()) {
            campos.putIfAbsent(error.getField(), error.getDefaultMessage());
        }
        return ResponseEntity.badRequest()
                .body(ErrorRespuesta.deValidacion("Hay campos invalidos en la solicitud", campos));
    }
}
