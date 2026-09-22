package com.consultorio.agenda;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.ZoneId;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class AgendaApplicationTests {

    @Autowired
    private Clock clock;

    @Test
    void elContextoArrancaYLasMigracionesCorren() {
        // Con ddl-auto=validate, que el contexto arranque significa que el esquema de
        // Flyway coincide con el mapeo JPA.
    }

    @Test
    void elRelojUsaLaZonaHorariaDelConsultorio() {
        assertThat(clock.getZone()).isEqualTo(ZoneId.of("America/Argentina/Buenos_Aires"));
    }
}
