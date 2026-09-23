package com.clinic.schedule.appointment.application;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.clinic.schedule.appointment.domain.Appointment;
import com.clinic.schedule.appointment.domain.PractitionerId;

/**
 * A practitioner's day, in order. The view reception works with all day, and the only thing a
 * practitioner may see.
 *
 * <p>Cancelled appointments are left out: this answers "what is happening today", not the
 * history, which is what the patient file will show.
 *
 * <p>Who may read whose agenda is decided here, not in the controller (D-04): reception reads
 * the practitioner it asks for; a practitioner always gets their own, whatever they asked for.
 * The answer says whose agenda it is, so the caller never has to guess.
 */
@Service
public class ViewDayAgendaUseCase {

    private final AppointmentRepository appointments;
    private final AgendaAccessPort access;

    public ViewDayAgendaUseCase(AppointmentRepository appointments, AgendaAccessPort access) {
        this.appointments = appointments;
        this.access = access;
    }

    @Transactional(readOnly = true)
    public DayAgenda handle(PractitionerId requested, LocalDate date) {
        PractitionerId practitionerId = access.restrictedTo().orElse(requested);
        List<Appointment> day = appointments.findBookedForPractitionerOn(practitionerId, date).stream()
                .sorted(Comparator.comparing(appointment -> appointment.slot().startsAt()))
                .toList();
        return new DayAgenda(practitionerId, day);
    }

    /** The agenda read, and whose it is — which, for a practitioner, may not be whom they asked for. */
    public record DayAgenda(PractitionerId practitionerId, List<Appointment> appointments) {
    }
}
