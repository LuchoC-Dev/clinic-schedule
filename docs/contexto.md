# Contexto del proyecto

## Qué es

Agenda interna para un consultorio médico pequeño (3 o 4 profesionales). Aplicación
Spring Boot construida por etapas: cada versión entrega algo útil y no adelanta
funcionalidad futura.

El objetivo del repositorio es doble: resolver el problema real de la agenda y
mostrar cómo la aplicación crece de forma ordenada.

## Quiénes la usan (v1)

- **Recepción**: administra turnos, horarios y ausencias de todos los profesionales.
  Puede consultar la agenda de cualquiera, incluso para mostrársela al profesional.
- **Profesional**: consulta únicamente sus propios turnos. Si necesita cambiar su
  horario o registrar una ausencia, se lo solicita a recepción.

Los pacientes **no** acceden al sistema en esta etapa. No reservan turnos ni tienen cuenta.

## Alcance de la v1

### Pacientes

Registro mínimo para poder asignar turnos:

- Identificador interno, nombre completo y DNI opcional.
- El DNI **no** es el identificador interno. Si se informa, es único.
- Se pueden registrar pacientes sin DNI.
- Puede haber varios pacientes con el mismo nombre.
- No existe ficha de paciente ni cuenta de acceso.

### Profesionales y disponibilidad

- Horario semanal habitual, pausas y ausencias. Los configura recepción.
- Un profesional por consultorio: no se gestionan recursos compartidos.
- Recepción registra la ausencia de un profesional por día o por intervalo horario.
  El sistema cancela automáticamente los turnos afectados que todavía **no** tengan
  la llegada del paciente confirmada. Los turnos con llegada confirmada requieren
  intervención manual.

### Razones y duración

- Cada profesional tiene su propia lista pequeña de razones: «consulta» y dos o tres
  más según el profesional.
- Cada razón tiene una duración fija **para ese profesional**.
- Al reservar, la capa de aplicación obtiene esa duración y se la pasa al dominio de
  reservas. El dominio trabaja con inicio y duración, y no conoce el significado de
  la razón.
- La duración es el tiempo reservado en la agenda. La atención real puede durar más;
  la v1 no intenta resolver esos retrasos.

### Turnos

- Recepción crea turnos (incluso para el mismo día), los reprograma y los cancela.
- Un turno tiene paciente, profesional, fecha y hora de inicio, duración, razón y
  una nota opcional.
- Estados iniciales: `RESERVADO` y `CANCELADO`. Los cancelados se conservan como registro.
- Reprogramar actualiza el turno. No se guarda el historial de fechas anteriores.
- No puede haber turnos superpuestos para un mismo profesional, ni turnos simultáneos
  para un mismo paciente.
- Un turno puede comenzar a cualquier hora disponible. La interfaz puede sugerir
  intervalos para facilitar la selección, sin imponer una grilla rígida al dominio.
- Las vistas de agenda son las más simples que permitan el trabajo diario.

### Llegada y cancelación automática

- El paciente pasa por recepción, que confirma su llegada.
- Sin llegada confirmada, el turno se cancela automáticamente **30 minutos** después
  de su hora de inicio.
- Si el paciente avisa que llegará tarde, recepción **suspende** esa cancelación. Si
  nunca llega, el turno se cancela **24 horas** después de su hora de inicio.
- Confirmar la llegada impide ambas cancelaciones automáticas.
- Regla provisional: cuando exista la funcionalidad de asistencia, se registrará una
  ausencia del paciente en lugar de cancelar el turno.

## Evolución prevista (NO implementar todavía)

Se documenta para tomar decisiones que no cierren puertas, no para adelantar código.

1. **Ficha de paciente**: registro interno consultado por el consultorio, con el
   historial de turnos. Por eso los turnos de la v1 se conservan siempre, incluidos
   los cancelados.
2. **Cola de llegada** por consultorio/profesional: admite pacientes sin turno y da
   prioridad a los que sí lo tienen. Ayuda a manejar la diferencia entre el horario
   reservado y el ritmo real de atención. Hasta que exista, recepción registra a un
   paciente sin turno creando un turno del mismo día.
3. **Más adelante**: historia clínica dentro de la ficha, asistencia con más estados,
   avisos o registro de cancelaciones, datos adicionales del paciente (teléfono, obra
   social) y reserva de turnos por la web.
4. **Reserva web**: cuenta con inicio de sesión vinculada a la ficha interna. Requiere
   DNI u otra forma de identificación y **nunca** usa el ID interno como identificación
   del paciente.
5. **Interfaz futura**: dos SPA separadas, una para recepción/profesionales y otra para
   pacientes. Por eso la API REST es el contrato principal desde la v1.
