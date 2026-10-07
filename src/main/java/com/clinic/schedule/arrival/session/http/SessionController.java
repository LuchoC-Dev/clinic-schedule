package com.clinic.schedule.arrival.session.http;

import java.util.UUID;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.clinic.schedule.arrival.session.command.AddPatientUseCase;
import com.clinic.schedule.arrival.session.command.AddUnidentifiedPatientUseCase;
import com.clinic.schedule.arrival.session.command.AppointmentArrivesUseCase;
import com.clinic.schedule.arrival.session.command.LiftReservationUseCase;
import com.clinic.schedule.arrival.session.command.ReassignUseCase;
import com.clinic.schedule.arrival.session.command.CloseQueueUseCase;
import com.clinic.schedule.arrival.session.command.DeclareEmergencyUseCase;
import com.clinic.schedule.arrival.session.command.MarkLeftUseCase;
import com.clinic.schedule.arrival.session.command.OpenQueueUseCase;
import com.clinic.schedule.arrival.session.command.TurnOffAutoCloseUseCase;
import com.clinic.schedule.arrival.session.domain.EntryId;
import com.clinic.schedule.arrival.session.query.ViewQueueSessionUseCase;

/**
 * A queue at work, for reception (D-63): opening and closing it, keeping it open past its day,
 * bringing patients in — charged in the same step, unless an emergency (D-87) —, making one an emergency,
 * saying one left. Each command is run and the
 * session read back, so the answer is what the reads see.
 */
@RestController
class SessionController {

    private final OpenQueueUseCase open;
    private final CloseQueueUseCase close;
    private final TurnOffAutoCloseUseCase turnOffAutoClose;
    private final AddPatientUseCase addPatient;
    private final DeclareEmergencyUseCase declareEmergency;
    private final MarkLeftUseCase markLeft;
    private final AddUnidentifiedPatientUseCase addUnidentified;
    private final AppointmentArrivesUseCase appointmentArrives;
    private final ReassignUseCase reassign;
    private final LiftReservationUseCase liftReservation;
    private final ViewQueueSessionUseCase view;

    SessionController(OpenQueueUseCase open, CloseQueueUseCase close, TurnOffAutoCloseUseCase turnOffAutoClose,
            AddPatientUseCase addPatient, DeclareEmergencyUseCase declareEmergency, MarkLeftUseCase markLeft,
            AddUnidentifiedPatientUseCase addUnidentified, AppointmentArrivesUseCase appointmentArrives,
            ReassignUseCase reassign, LiftReservationUseCase liftReservation, ViewQueueSessionUseCase view) {
        this.open = open;
        this.close = close;
        this.turnOffAutoClose = turnOffAutoClose;
        this.addPatient = addPatient;
        this.declareEmergency = declareEmergency;
        this.markLeft = markLeft;
        this.addUnidentified = addUnidentified;
        this.appointmentArrives = appointmentArrives;
        this.reassign = reassign;
        this.liftReservation = liftReservation;
        this.view = view;
    }

    @GetMapping("/api/queues/{id}/session")
    SessionResponse session(@PathVariable UUID id) {
        return SessionResponse.of(id, view.handle(id));
    }

    @PostMapping("/api/queues/{id}/open")
    SessionResponse open(@PathVariable UUID id) {
        open.handle(new OpenQueueUseCase.Command(id));
        return session(id);
    }

    @PostMapping("/api/queues/{id}/close")
    SessionResponse close(@PathVariable UUID id) {
        close.handle(new CloseQueueUseCase.Command(id));
        return session(id);
    }

    @PostMapping("/api/queues/{id}/auto-close-off")
    SessionResponse turnOffAutoClose(@PathVariable UUID id) {
        turnOffAutoClose.handle(new TurnOffAutoCloseUseCase.Command(id));
        return session(id);
    }

    @PostMapping("/api/queues/{id}/session/patients")
    @ResponseStatus(HttpStatus.CREATED)
    SessionResponse addPatient(@PathVariable UUID id, @Valid @RequestBody AddPatientRequest request) {
        addPatient.handle(new AddPatientUseCase.Command(id, request.patientId(), request.isEmergency(), request.amount()));
        return session(id);
    }

    /** A patient who gives no name: registered under an alias and brought in (D-60). */
    @PostMapping("/api/queues/{id}/session/unidentified")
    @ResponseStatus(HttpStatus.CREATED)
    SessionResponse addUnidentified(@PathVariable UUID id, @RequestBody(required = false) AddUnidentifiedRequest request) {
        addUnidentified.handle(new AddUnidentifiedPatientUseCase.Command(id, request != null && request.isEmergency(),
                request == null ? null : request.amount()));
        return session(id);
    }

    /** A patient with an appointment today arrives: into their practitioner's queue, kept for them (D-64). */
    @PostMapping("/api/appointment-arrivals")
    @ResponseStatus(HttpStatus.CREATED)
    SessionResponse appointmentArrives(@Valid @RequestBody AppointmentArrivalRequest request) {
        UUID queue = appointmentArrives.handle(new AppointmentArrivesUseCase.Command(request.appointmentId(), request.amount()));
        return session(queue);
    }

    @PostMapping("/api/queue-entries/{id}/reassign")
    void reassign(@PathVariable UUID id, @Valid @RequestBody ReassignRequest request) {
        reassign.handle(new ReassignUseCase.Command(new EntryId(id), request.practitionerId()));
    }

    @PostMapping("/api/queue-entries/{id}/lift-reservation")
    void liftReservation(@PathVariable UUID id) {
        liftReservation.handle(new LiftReservationUseCase.Command(new EntryId(id)));
    }

    @PostMapping("/api/queue-entries/{id}/emergency")
    void declareEmergency(@PathVariable UUID id) {
        declareEmergency.handle(new DeclareEmergencyUseCase.Command(new EntryId(id)));
    }

    @PostMapping("/api/queue-entries/{id}/left")
    void markLeft(@PathVariable UUID id) {
        markLeft.handle(new MarkLeftUseCase.Command(new EntryId(id)));
    }
}
