package com.clinic.schedule.appointment.application;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.clinic.schedule.appointment.domain.Appointment;
import com.clinic.schedule.appointment.domain.PractitionerId;

/**
 * The agenda over a span of days — one day or a week — for one practitioner or for all of
 * them, in order. The view reception works with all day, and the only thing a practitioner
 * may see.
 *
 * <p>Cancelled appointments are left out: this answers "what is happening", not the history,
 * which {@link ViewPatientAppointmentsUseCase} answers.
 *
 * <p>Who may read whose agenda is decided here, not in the controller (D-04): reception reads
 * the practitioner it asks for, or everyone when it asks for no one; a practitioner always gets
 * their own, whatever they asked for. The answer says whose agenda it is, so the caller never
 * has to guess.
 */
@Service
public class ViewAgendaUseCase {

    private final AppointmentRepository appointments;
    private final AgendaAccessPort access;

    public ViewAgendaUseCase(AppointmentRepository appointments, AgendaAccessPort access) {
        this.appointments = appointments;
        this.access = access;
    }

    /**
     * @param requested the practitioner asked for, or empty for every practitioner
     * @param from the first day, included
     * @param to the last day, included
     */
    @Transactional(readOnly = true)
    public Agenda handle(Optional<PractitionerId> requested, LocalDate from, LocalDate to) {
        if (to.isBefore(from)) {
            throw new IllegalArgumentException("the agenda cannot end before it starts: " + from + " to " + to);
        }
        Optional<PractitionerId> whose = access.restrictedTo().or(() -> requested);
        List<Appointment> found = whose
                .map(practitionerId -> appointments.findBookedForPractitionerBetween(practitionerId, from, to))
                .orElseGet(() -> appointments.findBookedBetween(from, to));
        return new Agenda(whose, found.stream()
                .sorted(Comparator.comparing(appointment -> appointment.slot().startsAt()))
                .toList());
    }

    /**
     * The agenda read, and whose it is — empty when it is everyone's. For a practitioner it may
     * not be whom they asked for.
     */
    public record Agenda(Optional<PractitionerId> practitionerId, List<Appointment> appointments) {
    }
}
