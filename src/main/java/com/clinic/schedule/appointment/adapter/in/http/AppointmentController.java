package com.clinic.schedule.appointment.adapter.in.http;

import java.net.URI;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.clinic.schedule.appointment.application.usecase.BookAppointmentUseCase;
import com.clinic.schedule.appointment.application.usecase.BookNewPatientUseCase;
import com.clinic.schedule.appointment.application.usecase.CancelAppointmentUseCase;
import com.clinic.schedule.appointment.application.usecase.FindArrivalsUseCase;
import com.clinic.schedule.appointment.application.usecase.RescheduleAppointmentUseCase;
import com.clinic.schedule.appointment.application.usecase.RecordLateArrivalNoticeUseCase;
import com.clinic.schedule.appointment.application.usecase.ViewAgendaUseCase;
import com.clinic.schedule.appointment.domain.Appointment;
import com.clinic.schedule.appointment.domain.AppointmentId;
import com.clinic.schedule.appointment.domain.PractitionerId;

import jakarta.validation.Valid;

/**
 * The appointment endpoints. Thin on purpose: translate the request, call the use case,
 * translate the answer. No rules live here.
 *
 * <p>Cancelling and rescheduling are {@code POST} on a sub-path rather than {@code DELETE}
 * and {@code PATCH}. They are business operations with rules of their own, not edits to a
 * field, and nothing is ever deleted — a cancelled appointment is kept as a record.
 */
@RestController
@RequestMapping("/api/appointments")
class AppointmentController {

    private final BookAppointmentUseCase book;
    private final BookNewPatientUseCase bookNewPatient;
    private final RescheduleAppointmentUseCase reschedule;
    private final CancelAppointmentUseCase cancel;
    private final FindArrivalsUseCase arrivals;
    private final RecordLateArrivalNoticeUseCase recordLateArrivalNotice;
    private final ViewAgendaUseCase agenda;

    AppointmentController(BookAppointmentUseCase book, BookNewPatientUseCase bookNewPatient,
            RescheduleAppointmentUseCase reschedule,
            CancelAppointmentUseCase cancel, FindArrivalsUseCase arrivals,
            RecordLateArrivalNoticeUseCase recordLateArrivalNotice, ViewAgendaUseCase agenda) {
        this.book = book;
        this.bookNewPatient = bookNewPatient;
        this.reschedule = reschedule;
        this.cancel = cancel;
        this.arrivals = arrivals;
        this.recordLateArrivalNotice = recordLateArrivalNotice;
        this.agenda = agenda;
    }

    /**
     * Books an appointment. It answers 201 even when the id was already known, because the
     * outcome the client asked for is the same either way; the body says which appointment
     * it ended up with.
     */
    @PostMapping
    ResponseEntity<AppointmentResponse> book(@Valid @RequestBody BookAppointmentRequest request) {
        Appointment booked = book.handle(request.toCommand());
        return ResponseEntity
                .created(URI.create("/api/appointments/" + booked.id().value()))
                .body(respond(booked));
    }

    /**
     * Registers a new patient and books them at once, in one transaction (D-75, D-100): a refused booking registers
     * nobody, and a patient the patient module refuses books nothing.
     */
    @PostMapping("/with-new-patient")
    ResponseEntity<AppointmentResponse> bookNewPatient(@Valid @RequestBody BookNewPatientRequest request) {
        Appointment booked = bookNewPatient.handle(request.toCommand());
        return ResponseEntity
                .created(URI.create("/api/appointments/" + booked.id().value()))
                .body(respond(booked));
    }

    @PostMapping("/{id}/reschedule")
    AppointmentResponse reschedule(@PathVariable UUID id,
            @Valid @RequestBody RescheduleAppointmentRequest request) {
        return respond(reschedule.handle(request.toCommand(id)));
    }

    /** Who asked for it comes in the body, with reception's note; who cancelled it, from the session (D-76). */
    @PostMapping("/{id}/cancel")
    AppointmentResponse cancel(@PathVariable UUID id, @Valid @RequestBody CancelAppointmentRequest request) {
        return respond(cancel.handle(request.toCommand(id)));
    }

    /** The patient arrived. The time is the practice's clock, not something the client sends. */

    /** The patient warned they will be late: wait until the end of the day instead of 30 minutes (D-61). */
    @PostMapping("/{id}/late-arrival-notice")
    AppointmentResponse recordLateArrivalNotice(@PathVariable UUID id) {
        return respond(recordLateArrivalNotice.handle(new AppointmentId(id)));
    }

    /**
     * A practitioner's day, in order, without the cancelled ones. A practitioner always gets
     * their own, whatever {@code practitionerId} says (D-04).
     */
    @GetMapping
    List<AppointmentResponse> dayAgenda(@RequestParam UUID practitionerId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        List<Appointment> found = agenda.handle(Optional.of(new PractitionerId(practitionerId)), date, date).appointments();
        Map<AppointmentId, LocalDateTime> arrived = arrivals.handle(found.stream().map(Appointment::id).toList());
        return found.stream()
                .map(appointment -> AppointmentResponse.of(appointment, arrived.get(appointment.id())))
                .toList();
    }

    /** The appointment as it is now, with when its patient arrived, if they did (D-65). */
    private AppointmentResponse respond(Appointment appointment) {
        return AppointmentResponse.of(appointment, arrivals.handle(List.of(appointment.id())).get(appointment.id()));
    }
}
