package com.clinic.schedule.appointment.application;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.clinic.schedule.appointment.application.PractitionerSchedulePort.BookableReason;
import com.clinic.schedule.appointment.domain.Appointment;
import com.clinic.schedule.appointment.domain.AppointmentId;
import com.clinic.schedule.appointment.domain.BookingService;
import com.clinic.schedule.appointment.domain.PatientId;
import com.clinic.schedule.appointment.domain.PractitionerId;
import com.clinic.schedule.appointment.domain.TimeSlot;

/**
 * Books an appointment:
 *
 * <ol>
 *   <li>look up how long the reason takes with this practitioner;</li>
 *   <li>check that the practitioner works for the whole slot;</li>
 *   <li>read what could collide, and let the domain decide;</li>
 *   <li>store the result.</li>
 * </ol>
 *
 * <p>The first two are the practitioner module's answers (D-08); the domain only ever sees
 * a start and a duration, and never learns what the reason means.
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
    private final PractitionerSchedulePort practitioners;
    private final BookingService bookingService;
    private final AgendaReader agenda;

    public BookAppointmentUseCase(AppointmentRepository appointments, PractitionerSchedulePort practitioners,
            BookingService bookingService) {
        this.appointments = appointments;
        this.practitioners = practitioners;
        this.bookingService = bookingService;
        this.agenda = new AgendaReader(appointments);
    }

    @Transactional
    public Appointment handle(Command command) {
        Optional<Appointment> alreadyBooked = appointments.findById(command.id());
        if (alreadyBooked.isPresent()) {
            return alreadyBooked.get();
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
                agenda.around(slot, command.practitionerId(), command.patientId()));

        appointments.save(booked);
        return booked;
    }

    /**
     * What reception fills in to book an appointment: who, with whom, what for and when. How
     * long it lasts is not asked — it follows from the reason (D-08).
     *
     * <p>{@code id} comes from the caller so a retried request reuses it instead of booking
     * twice. {@code reasonId} is the only bare {@code UUID}, so it cannot be swapped with the
     * wrapped ids by accident.
     */
    public record Command(
            AppointmentId id,
            PatientId patientId,
            PractitionerId practitionerId,
            UUID reasonId,
            LocalDateTime startsAt,
            String note) {
    }
}
