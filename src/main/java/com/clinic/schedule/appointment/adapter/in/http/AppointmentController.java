package com.clinic.schedule.appointment.adapter.in.http;

import java.net.URI;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
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
import com.clinic.schedule.appointment.application.usecase.CancelAppointmentUseCase;
import com.clinic.schedule.appointment.application.usecase.CheckInUseCase;
import com.clinic.schedule.appointment.application.usecase.RescheduleAppointmentUseCase;
import com.clinic.schedule.appointment.application.usecase.SuspendAutoCancellationUseCase;
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
    private final RescheduleAppointmentUseCase reschedule;
    private final CancelAppointmentUseCase cancel;
    private final CheckInUseCase checkIn;
    private final SuspendAutoCancellationUseCase suspendAutoCancellation;
    private final ViewAgendaUseCase agenda;

    AppointmentController(BookAppointmentUseCase book, RescheduleAppointmentUseCase reschedule,
            CancelAppointmentUseCase cancel, CheckInUseCase checkIn,
            SuspendAutoCancellationUseCase suspendAutoCancellation, ViewAgendaUseCase agenda) {
        this.book = book;
        this.reschedule = reschedule;
        this.cancel = cancel;
        this.checkIn = checkIn;
        this.suspendAutoCancellation = suspendAutoCancellation;
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
                .body(AppointmentResponse.of(booked));
    }

    @PostMapping("/{id}/reschedule")
    AppointmentResponse reschedule(@PathVariable UUID id,
            @Valid @RequestBody RescheduleAppointmentRequest request) {
        return AppointmentResponse.of(reschedule.handle(request.toCommand(id)));
    }

    @PostMapping("/{id}/cancel")
    AppointmentResponse cancel(@PathVariable UUID id) {
        return AppointmentResponse.of(cancel.handle(new AppointmentId(id)));
    }

    /** The patient arrived. The time is the practice's clock, not something the client sends. */
    @PostMapping("/{id}/check-in")
    AppointmentResponse checkIn(@PathVariable UUID id) {
        return AppointmentResponse.of(checkIn.handle(new AppointmentId(id)));
    }

    /** The patient warned they will be late: wait 24 hours instead of 30 minutes. */
    @PostMapping("/{id}/suspend-auto-cancellation")
    AppointmentResponse suspendAutoCancellation(@PathVariable UUID id) {
        return AppointmentResponse.of(suspendAutoCancellation.handle(new AppointmentId(id)));
    }

    /**
     * A practitioner's day, in order, without the cancelled ones. A practitioner always gets
     * their own, whatever {@code practitionerId} says (D-04).
     */
    @GetMapping
    List<AppointmentResponse> dayAgenda(@RequestParam UUID practitionerId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return agenda.handle(Optional.of(new PractitionerId(practitionerId)), date, date).appointments().stream()
                .map(AppointmentResponse::of)
                .toList();
    }
}
