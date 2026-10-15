package com.clinic.schedule.appointment.adapter.in.http;

import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.clinic.schedule.appointment.application.usecase.RecordTimeOffUseCase;
import com.clinic.schedule.patient.api.PatientReader;
import com.clinic.schedule.patient.api.PatientSummary;

import jakarta.validation.Valid;

/**
 * Records a practitioner's absence and answers with what it did to the agenda.
 *
 * <p>The route is {@code /api/time-off} and not {@code /api/practitioners/{id}/time-off}: this
 * module serves it, because the operation that matters is the cascade over the appointments,
 * and a route under another module's resource would hide who answers it (D-32).
 */
@RestController
@RequestMapping("/api/time-off")
class TimeOffController {

    private final RecordTimeOffUseCase recordTimeOff;
    private final PatientReader patients;

    TimeOffController(RecordTimeOffUseCase recordTimeOff, PatientReader patients) {
        this.recordTimeOff = recordTimeOff;
        this.patients = patients;
    }

    @PostMapping
    ResponseEntity<TimeOffResponse> record(@Valid @RequestBody TimeOffRequest request) {
        RecordTimeOffUseCase.Command command = request.toCommand();
        RecordTimeOffUseCase.Result result = recordTimeOff.handle(command);
        // The patients' names, as the screen says what the absence did; the use case knows them by id alone (D-33).
        Map<UUID, String> names = patients.findAll(Stream.concat(result.cancelled().stream(),
                                result.needsManualHandling().stream())
                        .map(appointment -> appointment.patientId().value())
                        .distinct()
                        .toList())
                .stream()
                .collect(Collectors.toMap(PatientSummary::id, PatientSummary::fullName));
        return ResponseEntity.status(HttpStatus.CREATED).body(TimeOffResponse.of(request, command, result, names));
    }
}
