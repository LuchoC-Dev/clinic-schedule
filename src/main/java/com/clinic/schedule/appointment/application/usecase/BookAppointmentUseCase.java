package com.clinic.schedule.appointment.application.usecase;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.clinic.schedule.appointment.application.exception.OutsideWorkingHoursException;
import com.clinic.schedule.appointment.application.exception.UnknownPatientException;
import com.clinic.schedule.appointment.application.exception.UnknownReasonException;
import com.clinic.schedule.appointment.application.port.AppointmentRepository;
import com.clinic.schedule.appointment.application.port.PatientRegistryPort;
import com.clinic.schedule.appointment.application.port.PractitionerSchedulePort.BookableReason;
import com.clinic.schedule.appointment.application.port.PractitionerSchedulePort;
import com.clinic.schedule.appointment.domain.Appointment;
import com.clinic.schedule.appointment.domain.AppointmentId;
import com.clinic.schedule.appointment.domain.BookingChannel;
import com.clinic.schedule.appointment.domain.BookingService;
import com.clinic.schedule.appointment.domain.PatientId;
import com.clinic.schedule.appointment.domain.PractitionerId;
import com.clinic.schedule.appointment.domain.TimeSlot;

/**
 * Books an appointment:
 *
 * <ol>
 *   <li>check that the patient is registered;</li>
 *   <li>look up how long the reason takes with this practitioner;</li>
 *   <li>check that the practitioner works for the whole slot;</li>
 *   <li>read what could collide, and let the domain decide;</li>
 *   <li>store the result.</li>
 * </ol>
 *
 * <p>The first three are other modules' answers (D-08, D-29); the domain only ever sees a
 * start and a duration, and never learns what the reason means.
 *
 * <p>Retrying is safe. An id that already exists means this request already went through,
 * so the existing appointment is returned instead of booking a second one (D-19).
 *
 * <p>The whole thing runs in one transaction: reading the agenda and storing the appointment
 * have to be a single step (D-21).
 */
@Service
public class BookAppointmentUseCase {

    private final AppointmentRepository appointments;
    private final PatientRegistryPort patients;
    private final PractitionerSchedulePort practitioners;
    private final BookingService bookingService;
    private final AgendaReader agenda;
    private final Clock clock;

    public BookAppointmentUseCase(AppointmentRepository appointments, PatientRegistryPort patients,
            PractitionerSchedulePort practitioners, BookingService bookingService, Clock clock) {
        this.appointments = appointments;
        this.patients = patients;
        this.practitioners = practitioners;
        this.bookingService = bookingService;
        this.agenda = new AgendaReader(appointments);
        this.clock = clock;
    }

    @Transactional
    public Appointment handle(Command command) {
        Optional<Appointment> alreadyBooked = appointments.findById(command.id());
        if (alreadyBooked.isPresent()) {
            return alreadyBooked.get();
        }

        if (!patients.exists(command.patientId())) {
            throw new UnknownPatientException(command.patientId());
        }

        BookableReason reason = practitioners.findReason(command.practitionerId(), command.reasonId())
                .orElseThrow(() -> new UnknownReasonException(command.practitionerId(), command.reasonId()));
        TimeSlot slot = TimeSlot.of(command.startsAt(), reason.durationMinutes());
        if (!practitioners.worksDuring(command.practitionerId(), slot)) {
            throw new OutsideWorkingHoursException(command.practitionerId(), slot);
        }

        Appointment booked = bookingService.book(
                command.id(),
                command.patientId(),
                command.practitionerId(),
                slot,
                reason.name(),
                command.note(),
                LocalDateTime.now(clock),
                agenda.around(slot, command.practitionerId(), command.patientId()),
                command.bookedVia());

        appointments.save(booked);
        return booked;
    }

    /**
     * What reception fills in to book an appointment: who, with whom, what for and when. How
     * long it lasts is not asked — it follows from the reason (D-08).
     *
     * <p>{@code id} comes from the caller so a retried request reuses it instead of booking
     * twice. {@code reasonId} is the only bare {@code UUID}, so it cannot be swapped with the
     * wrapped ids by accident. {@code bookedVia} says whether reception or the patient on the web
     * books it (D-82).
     */
    public record Command(
            AppointmentId id,
            PatientId patientId,
            PractitionerId practitionerId,
            UUID reasonId,
            LocalDateTime startsAt,
            String note,
            BookingChannel bookedVia) {
    }
}
