package com.clinic.schedule.portal.domain;

import java.time.LocalDateTime;
import java.time.Period;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;

/**
 * The appointments a patient sees of their own (D-80): those ahead, the soonest first, and those of the last
 * 12 months, the most recent first. Older ones are left out: reception still has them.
 */
public record OwnAppointments(List<Appointment> upcoming, List<Appointment> past) {

    /** How far back a patient reads their appointments. */
    static final Period LOOK_BACK = Period.ofMonths(12);

    /** An appointment from now on is ahead, whatever its state; one earlier today is past. */
    public static OwnAppointments of(Collection<Appointment> appointments, LocalDateTime now) {
        LocalDateTime since = now.toLocalDate().minus(LOOK_BACK).atStartOfDay();
        Comparator<Appointment> byTime = Comparator.comparing(Appointment::startsAt);
        return new OwnAppointments(
                appointments.stream()
                        .filter(appointment -> !appointment.startsAt().isBefore(now))
                        .sorted(byTime)
                        .toList(),
                appointments.stream()
                        .filter(appointment -> appointment.startsAt().isBefore(now)
                                && !appointment.startsAt().isBefore(since))
                        .sorted(byTime.reversed())
                        .toList());
    }
}
