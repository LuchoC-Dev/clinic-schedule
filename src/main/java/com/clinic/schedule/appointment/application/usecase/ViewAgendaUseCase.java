package com.clinic.schedule.appointment.application.usecase;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.clinic.schedule.appointment.application.exception.AgendaAccessDeniedException;
import com.clinic.schedule.appointment.application.port.AgendaAccessPort;
import com.clinic.schedule.appointment.application.port.AppointmentRepository;
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
 * the practitioner it asks for, or everyone when it asks for no one. A practitioner reads their
 * own: asking for no one gives them theirs, and asking for someone else is refused (D-39). The
 * answer says whose agenda it is, so the caller never has to guess.
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
     * @throws AgendaAccessDeniedException when a practitioner asks for another one's agenda
     */
    @Transactional(readOnly = true)
    public Agenda handle(Optional<PractitionerId> requested, LocalDate from, LocalDate to) {
        if (to.isBefore(from)) {
            throw new IllegalArgumentException("the agenda cannot end before it starts: " + from + " to " + to);
        }
        Optional<PractitionerId> restrictedTo = access.restrictedTo();
        if (restrictedTo.isPresent() && requested.isPresent() && !requested.equals(restrictedTo)) {
            throw new AgendaAccessDeniedException(restrictedTo.get(), requested.get());
        }
        Optional<PractitionerId> whose = restrictedTo.or(() -> requested);
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
