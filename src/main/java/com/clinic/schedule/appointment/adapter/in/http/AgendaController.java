package com.clinic.schedule.appointment.adapter.in.http;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.clinic.schedule.appointment.application.usecase.FindAppointmentUseCase;
import com.clinic.schedule.appointment.application.usecase.FindArrivalsUseCase;
import com.clinic.schedule.appointment.application.usecase.SuggestSlotsUseCase;
import com.clinic.schedule.appointment.application.usecase.ViewAgendaUseCase;
import com.clinic.schedule.appointment.application.usecase.ViewPatientAppointmentsUseCase;
import com.clinic.schedule.appointment.domain.Appointment;
import com.clinic.schedule.appointment.domain.AppointmentId;
import com.clinic.schedule.appointment.domain.PatientId;
import com.clinic.schedule.appointment.domain.PractitionerId;
import com.clinic.schedule.patient.api.PatientReader;
import com.clinic.schedule.patient.api.PatientSummary;
import com.clinic.schedule.practitioner.api.PractitionerReader;
import com.clinic.schedule.practitioner.api.PractitionerSummary;

/**
 * Reception's agenda for its application (D-98, D-100): what the agenda screens put together, the names of
 * patients and practitioners included, which the use cases do not need and the screens do (D-33).
 *
 * <ul>
 *   <li>A day's agenda, or its week from Monday to Sunday; without a practitioner, everyone's; without a day,
 *       today's, by the practice's clock, so the application never trusts the browser's.</li>
 *   <li>The free times to book a reason on a day, or to move an appointment to it, as the booking screen
 *       offers them.</li>
 *   <li>A patient's appointments, cancelled ones included, with who cancelled each.</li>
 * </ul>
 *
 * <p>Reception's alone for now, by route (D-34): the practitioners' application reads its agenda when it is
 * built.
 */
@RestController
class AgendaController {

    private final ViewAgendaUseCase agenda;
    private final ViewPatientAppointmentsUseCase patientAppointments;
    private final FindAppointmentUseCase findAppointment;
    private final SuggestSlotsUseCase suggestSlots;
    private final FindArrivalsUseCase arrivals;
    private final PractitionerReader practitioners;
    private final PatientReader patients;
    private final Clock clock;

    AgendaController(ViewAgendaUseCase agenda, ViewPatientAppointmentsUseCase patientAppointments,
            FindAppointmentUseCase findAppointment, SuggestSlotsUseCase suggestSlots, FindArrivalsUseCase arrivals,
            PractitionerReader practitioners, PatientReader patients, Clock clock) {
        this.agenda = agenda;
        this.patientAppointments = patientAppointments;
        this.findAppointment = findAppointment;
        this.suggestSlots = suggestSlots;
        this.arrivals = arrivals;
        this.practitioners = practitioners;
        this.patients = patients;
        this.clock = clock;
    }

    @GetMapping("/api/agenda")
    AgendaResponse days(@RequestParam(required = false) UUID practitionerId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(defaultValue = "false") boolean week) {
        LocalDate day = date != null ? date : LocalDate.now(clock);
        LocalDate from = week ? day.with(DayOfWeek.MONDAY) : day;
        LocalDate to = week ? from.plusDays(6) : day;
        ViewAgendaUseCase.Agenda read = agenda.handle(Optional.ofNullable(practitionerId).map(PractitionerId::new),
                from, to);
        List<PractitionerSummary> everyone = practitioners.findAll();
        return new AgendaResponse(
                day, from, to,
                read.practitionerId().map(PractitionerId::value).orElse(null),
                everyone.stream().map(each -> new AgendaResponse.Practitioner(each.id(), each.fullName())).toList(),
                rows(read.appointments(), everyone));
    }

    /**
     * Without an appointment, the times a reason of the practitioner fits on that day; with one, the times it may
     * be moved to, its own not counted as taken. A day the practitioner does not work at all is said apart from a
     * full one.
     */
    @GetMapping("/api/agenda/free-times")
    FreeTimesResponse freeTimes(@RequestParam(required = false) UUID practitionerId,
            @RequestParam(required = false) UUID reasonId, @RequestParam(required = false) UUID appointmentId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        SuggestSlotsUseCase.Suggestions found;
        if (appointmentId != null) {
            found = suggestSlots.forMoving(findAppointment.handle(new AppointmentId(appointmentId)), date);
        } else if (practitionerId != null && reasonId != null) {
            found = suggestSlots.forReason(new PractitionerId(practitionerId), reasonId, date);
        } else {
            throw new IllegalArgumentException("either an appointment, or a practitioner and a reason, are required");
        }
        return new FreeTimesResponse(date, found.practitionerWorks(), found.times());
    }

    @GetMapping("/api/patients/{id}/appointments")
    List<AgendaResponse.Appointment> ofPatient(@PathVariable UUID id) {
        return rows(patientAppointments.handle(new PatientId(id)), practitioners.findAll());
    }

    private List<AgendaResponse.Appointment> rows(List<Appointment> found, List<PractitionerSummary> everyone) {
        Map<UUID, String> practitionerNames = everyone.stream()
                .collect(Collectors.toMap(PractitionerSummary::id, PractitionerSummary::fullName));
        Map<UUID, PatientSummary> patientsById = patients.findAll(
                        found.stream().map(appointment -> appointment.patientId().value()).distinct().toList())
                .stream()
                .collect(Collectors.toMap(PatientSummary::id, Function.identity()));
        Map<AppointmentId, LocalDateTime> arrived = arrivals.handle(found.stream().map(Appointment::id).toList());
        return found.stream()
                .map(appointment -> AgendaResponse.Appointment.of(appointment,
                        patientsById.get(appointment.patientId().value()),
                        practitionerNames.get(appointment.practitionerId().value()), arrived.get(appointment.id())))
                .toList();
    }

    /** {@code practitionerWorks} false, with no times, when the practitioner does not work that day at all. */
    record FreeTimesResponse(LocalDate date, boolean practitionerWorks, List<LocalTime> times) {
    }
}
