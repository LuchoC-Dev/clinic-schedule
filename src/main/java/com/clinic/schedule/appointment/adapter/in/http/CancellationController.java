package com.clinic.schedule.appointment.adapter.in.http;

import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.clinic.schedule.appointment.application.usecase.ListCancellationsUseCase;
import com.clinic.schedule.appointment.domain.PractitionerId;
import com.clinic.schedule.patient.api.PatientReader;
import com.clinic.schedule.patient.api.PatientSummary;
import com.clinic.schedule.practitioner.api.PractitionerReader;
import com.clinic.schedule.practitioner.api.PractitionerSummary;

/**
 * The cancellations, by the days they were made and by practitioner (D-76). Two routes, one per role,
 * each with an answer of its own, so what each role sees is decided by the route (D-34):
 * {@code /api/cancellations} is reception's, whole; {@code /api/cancellation-log} is the admins',
 * without the patient and the note.
 */
@RestController
class CancellationController {

    private final ListCancellationsUseCase cancellations;
    private final PatientReader patients;
    private final PractitionerReader practitioners;

    CancellationController(ListCancellationsUseCase cancellations, PatientReader patients,
            PractitionerReader practitioners) {
        this.cancellations = cancellations;
        this.patients = patients;
        this.practitioners = practitioners;
    }

    @GetMapping("/api/cancellations")
    CancellationsResponse forReception(@RequestParam(required = false) UUID practitionerId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        ListCancellationsUseCase.Result result = list(practitionerId, from, to);
        Map<UUID, PatientSummary> patientsById = patients.findAll(result.cancelled().stream()
                        .map(appointment -> appointment.patientId().value())
                        .distinct()
                        .toList())
                .stream()
                .collect(Collectors.toMap(PatientSummary::id, Function.identity()));
        return CancellationsResponse.of(result, patientsById, practitionerNames());
    }

    @GetMapping("/api/cancellation-log")
    CancellationLogResponse forAdmins(@RequestParam(required = false) UUID practitionerId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return CancellationLogResponse.of(list(practitionerId, from, to), practitionerNames());
    }

    private ListCancellationsUseCase.Result list(UUID practitionerId, LocalDate from, LocalDate to) {
        return cancellations.handle(new ListCancellationsUseCase.Command(
                practitionerId == null ? null : new PractitionerId(practitionerId), from, to));
    }

    private Map<UUID, String> practitionerNames() {
        return practitioners.findAll().stream()
                .collect(Collectors.toMap(PractitionerSummary::id, PractitionerSummary::fullName));
    }
}
