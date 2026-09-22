package com.consultorio.agenda.config;

import java.time.Clock;
import java.time.ZoneId;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Zona horaria fija de la aplicacion (D-10). El resto del codigo usa este {@link Clock}
 * en lugar de {@code LocalDateTime.now()}, para que los plazos de cancelacion automatica
 * sean testeables con un reloj fijo.
 */
@Configuration
class ConfiguracionDeTiempo {

    @Bean
    Clock clock(@Value("${app.zona-horaria}") String zonaHoraria) {
        return Clock.system(ZoneId.of(zonaHoraria));
    }
}
