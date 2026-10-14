package com.clinic.schedule.appointment.adapter.in.http;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.clinic.schedule.appointment.application.usecase.FindArrivalsUseCase;
import com.clinic.schedule.appointment.application.usecase.ViewAgendaUseCase;
import com.clinic.schedule.appointment.domain.Appointment;
import com.clinic.schedule.appointment.domain.AppointmentId;
import com.clinic.schedule.appointment.domain.PractitionerId;
import com.clinic.schedule.patient.api.PatientReader;
import com.clinic.schedule.patient.api.PatientSummary;
import com.clinic.schedule.practitioner.api.PractitionerReader;
import com.clinic.schedule.practitioner.api.PractitionerSummary;

/**
 * Reception's agenda of a day, for its application (D-98): what the agenda screen puts together, the names of
 * patients and practitioners included, which the use case does not need and the screen does (D-33). Without a
 * practitioner it is everyone's; without a day, today's, by the practice's clock, so the application never
 * trusts the browser's.
 *
 * <p>Reception's alone for now, by route (D-34): the practitioners' application reads it when it is built.
 */
@RestController
class AgendaController {

    private final ViewAgendaUseCase agenda;
    private final FindArrivalsUseCase arrivals;
    private final PractitionerReader practitioners;
    private final PatientReader patients;
    private final Clock clock;

    AgendaController(ViewAgendaUseCase agenda, FindArrivalsUseCase arrivals, PractitionerReader practitioners,
            PatientReader patients, Clock clock) {
        this.agenda = agenda;
        this.arrivals = arrivals;
        this.practitioners = practitioners;
        this.patients = patients;
        this.clock = clock;
    }

    @GetMapping("/api/agenda")
    AgendaResponse day(@RequestParam(required = false) UUID practitionerId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        LocalDate day = date != null ? date : LocalDate.now(clock);
        ViewAgendaUseCase.Agenda read = agenda.handle(Optional.ofNullable(practitionerId).map(PractitionerId::new),
                day, day);
        List<PractitionerSummary> everyone = practitioners.findAll();
        Map<UUID, String> practitionerNames = everyone.stream()
                .collect(Collectors.toMap(PractitionerSummary::id, PractitionerSummary::fullName));
        Map<UUID, PatientSummary> patientsById = patients.findAll(
                        read.appointments().stream().map(appointment -> appointment.patientId().value()).toList())
                .stream()
                .collect(Collectors.toMap(PatientSummary::id, Function.identity()));
        Map<AppointmentId, LocalDateTime> arrived = arrivals.handle(
                read.appointments().stream().map(Appointment::id).toList());
        return new AgendaResponse(
                day,
                read.practitionerId().map(PractitionerId::value).orElse(null),
                everyone.stream().map(each -> new AgendaResponse.Practitioner(each.id(), each.fullName())).toList(),
                read.appointments().stream()
                        .map(appointment -> rowOf(appointment, patientsById.get(appointment.patientId().value()),
                                practitionerNames.get(appointment.practitionerId().value()),
                                arrived.get(appointment.id())))
                        .toList());
    }

    private static AgendaResponse.Appointment rowOf(Appointment appointment, PatientSummary patient,
            String practitioner, LocalDateTime arrivedAt) {
        return new AgendaResponse.Appointment(
                appointment.id().value(),
                appointment.slot().startsAt(),
                appointment.slot().endsAt(),
                (int) appointment.slot().duration().toMinutes(),
                appointment.patientId().value(),
                patient == null ? null : patient.fullName(),
                patient == null ? null : patient.document(),
                appointment.practitionerId().value(),
                practitioner,
                appointment.reason(),
                appointment.note(),
                appointment.status(),
                arrivedAt,
                appointment.hasLateArrivalNotice(),
                appointment.bookedVia(),
                appointment.movedOnWeb());
    }
}
