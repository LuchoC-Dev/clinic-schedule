package com.clinic.schedule.appointment.application.usecase;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.clinic.schedule.appointment.application.port.AppointmentRepository;
import com.clinic.schedule.appointment.domain.Appointment;
import com.clinic.schedule.appointment.domain.PractitionerId;

/**
 * The cancellations, by the days they were made and by practitioner, the most recent first (D-76).
 * Reception and the admins read them, each through a route of their own that says what they see of
 * each one (D-34): the use case hands the appointments over whole, and the admins' adapters leave
 * out the patient and the note.
 *
 * <p>Without days, the last week. At most {@value #LIMIT} come back, and the answer says when there
 * were more, so a narrower search finds them — as the readings of the clinical records do (D-71).
 */
@Service
public class ListCancellationsUseCase {

    static final int LIMIT = 500;
    private static final int DEFAULT_DAYS = 7;

    private final AppointmentRepository appointments;
    private final Clock clock;

    public ListCancellationsUseCase(AppointmentRepository appointments, Clock clock) {
        this.appointments = appointments;
        this.clock = clock;
    }

    /** @throws IllegalArgumentException when the first day comes after the last */
    @Transactional(readOnly = true)
    public Result handle(Command command) {
        LocalDate to = command.to() != null ? command.to() : LocalDate.now(clock);
        LocalDate from = command.from() != null ? command.from() : to.minusDays(DEFAULT_DAYS - 1);
        if (from.isAfter(to)) {
            throw new IllegalArgumentException("The first day comes after the last");
        }
        List<Appointment> found = appointments.findCancelledBetween(command.practitioner(), from.atStartOfDay(),
                to.plusDays(1).atStartOfDay(), LIMIT + 1);
        return new Result(found.stream().limit(LIMIT).toList(), found.size() > LIMIT, from, to);
    }

    /**
     * @param practitioner only this practitioner's appointments; null for everyone's
     * @param from the first day, included; null for a week before the last
     * @param to the last day, included; null for today
     */
    public record Command(PractitionerId practitioner, LocalDate from, LocalDate to) {
    }

    /** The cancelled appointments found, whether there were more than came back, and the days searched. */
    public record Result(List<Appointment> cancelled, boolean limitReached, LocalDate from, LocalDate to) {
    }
}
