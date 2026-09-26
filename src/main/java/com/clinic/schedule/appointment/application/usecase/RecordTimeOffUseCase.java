package com.clinic.schedule.appointment.application.usecase;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.clinic.schedule.appointment.application.exception.TimeOffInThePastException;
import com.clinic.schedule.appointment.application.exception.UnknownPractitionerException;
import com.clinic.schedule.appointment.application.port.AppointmentRepository;
import com.clinic.schedule.appointment.application.port.ArrivalPort;
import com.clinic.schedule.appointment.application.port.PractitionerSchedulePort;
import com.clinic.schedule.appointment.domain.Appointment;
import com.clinic.schedule.appointment.domain.PractitionerId;
import com.clinic.schedule.appointment.domain.TimeSlot;

/**
 * Records a practitioner's time off and handles the appointments it hits (D-32):
 *
 * <ul>
 *   <li>those whose patient has not arrived are cancelled automatically;</li>
 *   <li>those whose patient already arrived are left as they are and reported, because a
 *       person is waiting and reception has to decide what to do.</li>
 * </ul>
 *
 * <p>It lives in this module and not in the practitioner one because the consequences are
 * about appointments, and the practitioner module does not know them. The absence is stored
 * through the practitioner's api inside this same transaction, so the absence and its
 * consequences are stored together or not at all.
 *
 * <p>Future booking during the absence is refused by the availability check, like any time
 * outside the practitioner's hours.
 */
@Service
public class RecordTimeOffUseCase {

    private final AppointmentRepository appointments;
    private final PractitionerSchedulePort practitioners;
    private final ArrivalPort arrivals;
    private final Clock clock;

    public RecordTimeOffUseCase(AppointmentRepository appointments, PractitionerSchedulePort practitioners,
            ArrivalPort arrivals, Clock clock) {
        this.appointments = appointments;
        this.practitioners = practitioners;
        this.arrivals = arrivals;
        this.clock = clock;
    }

    /**
     * @throws TimeOffInThePastException when the absence is on a day already gone: there is
     *         nothing left to cancel, so recording it can only be a mistyped date (D-38). Today
     *         is accepted whole, so an absence reported in the morning can start at midnight.
     */
    @Transactional
    public Result handle(Command command) {
        if (command.span().startsAt().toLocalDate().isBefore(LocalDate.now(clock))) {
            throw new TimeOffInThePastException(command.span().startsAt().toLocalDate());
        }
        if (!practitioners.recordTimeOff(command.practitionerId(), command.span())) {
            throw new UnknownPractitionerException(command.practitionerId());
        }

        List<Appointment> hit = bookedDuring(command.practitionerId(), command.span());

        List<Appointment> cancelled = hit.stream()
                .filter(appointment -> !appointment.isCheckedIn())
                .map(Appointment::cancelled)
                .toList();
        cancelled.forEach(appointments::save);
        cancelled.forEach(appointment -> arrivals.appointmentCancelled(appointment.id()));

        List<Appointment> needsManualHandling = hit.stream().filter(Appointment::isCheckedIn).toList();

        return new Result(cancelled, needsManualHandling);
    }

    /** Every day the span touches is read whole, and the overlap decided on each appointment. */
    private List<Appointment> bookedDuring(PractitionerId practitionerId, TimeSlot span) {
        List<Appointment> hit = new ArrayList<>();
        for (LocalDate day = span.startsAt().toLocalDate();
                day.atStartOfDay().isBefore(span.endsAt());
                day = day.plusDays(1)) {
            for (Appointment appointment : appointments.findBookedForPractitionerOn(practitionerId, day)) {
                if (appointment.slot().overlaps(span)) {
                    hit.add(appointment);
                }
            }
        }
        return hit;
    }

    /** The practitioner and the span they are absent for: whole days, or part of one day. */
    public record Command(PractitionerId practitionerId, TimeSlot span) {

        /**
         * The absence as reception describes it (D-46): from a day, until another day or the same
         * one, and optionally between two times. Without times it is whole days; with both, part
         * of a single day. Several days are always whole: an absence from Monday afternoon to
         * Wednesday noon is recorded as two.
         *
         * @param untilDate the last day of the absence, included; null for a single day
         * @throws IllegalArgumentException when the combination does not describe an absence
         */
        public static Command of(PractitionerId practitionerId, LocalDate date, LocalDate untilDate,
                LocalTime startsAt, LocalTime endsAt) {
            LocalDate lastDay = untilDate != null ? untilDate : date;
            if (lastDay.isBefore(date)) {
                throw new IllegalArgumentException("Time off cannot end on a day before it starts: " + date + " to " + lastDay);
            }
            if ((startsAt == null) != (endsAt == null)) {
                throw new IllegalArgumentException("Time off needs both a start and an end time, or neither for whole days");
            }
            if (startsAt == null) {
                LocalDateTime from = date.atStartOfDay();
                return new Command(practitionerId,
                        new TimeSlot(from, Duration.between(from, lastDay.plusDays(1).atStartOfDay())));
            }
            if (!lastDay.equals(date)) {
                throw new IllegalArgumentException("Time off over several days is recorded as whole days, without times");
            }
            LocalDateTime from = date.atTime(startsAt);
            return new Command(practitionerId, new TimeSlot(from, Duration.between(from, date.atTime(endsAt))));
        }
    }

    /**
     * What the absence did: the appointments it cancelled, and the ones it could not because
     * the patient is already there.
     */
    public record Result(List<Appointment> cancelled, List<Appointment> needsManualHandling) {
    }
}
