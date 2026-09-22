# Agenda de consultorio

Agenda interna para un consultorio médico pequeño (3 o 4 profesionales). Recepción
administra turnos, horarios y ausencias; cada profesional consulta únicamente sus
propios turnos.

Se construye por etapas. El alcance funcional está en [`docs/contexto.md`](docs/contexto.md),
las decisiones técnicas en [`docs/decisiones.md`](docs/decisiones.md) y el avance en
[`docs/build.md`](docs/build.md).

## Requisitos

- **JDK 21.** En esta máquina está en `C:\Program Files\Java\jdk-21`. El `java` del
  `PATH` es el 25, así que hay que apuntar `JAVA_HOME` al 21 antes de compilar:

  ```powershell
  $env:JAVA_HOME = "C:\Program Files\Java\jdk-21"
  ```

- Maven no hace falta instalarlo: el repositorio incluye el wrapper (`mvnw`).

## Comandos

```powershell
.\mvnw test                # suite completa
.\mvnw spring-boot:run     # levanta la app en http://localhost:8080
```

La base es H2 en memoria: se crea y se puebla con Flyway en cada arranque, y se pierde
al detener la aplicación.

## Usuarios de desarrollo

Definidos en `V2__semilla_usuarios.sql`. Hay que reemplazarlos antes de cualquier
despliegue real.

| Usuario     | Contraseña       | Rol         | Profesional |
|-------------|------------------|-------------|-------------|
| `recepcion` | `recepcion123`   | RECEPCION   | —           |
| `arias`     | `profesional123` | PROFESIONAL | 1           |
| `benitez`   | `profesional123` | PROFESIONAL | 2           |

## Estado actual

Etapa 1 terminada: esqueleto, seguridad con dos roles, migraciones y contrato de errores.

```powershell
curl.exe -u recepcion:recepcion123 http://localhost:8080/api/sesion
```

```json
{"id":1,"usuario":"recepcion","rol":"RECEPCION","profesionalId":null}
```

El formulario de login está en `http://localhost:8080/login`. Las pantallas de agenda
llegan en la etapa 7.

## Estructura

```
src/main/java/com/consultorio/agenda/
├── AgendaApplication.java
├── config/            reloj y zona horaria de la aplicación
├── compartido/        excepciones y contrato de errores de la API
└── seguridad/         usuarios, roles y sesión actual
    └── api/           lo único que ven los otros módulos
```

Los módulos `paciente`, `profesional` y `turno` se agregan en las etapas 2, 3 y 4.
Un módulo solo usa el paquete `api/` de otro: ver D-05 en `docs/decisiones.md`.
