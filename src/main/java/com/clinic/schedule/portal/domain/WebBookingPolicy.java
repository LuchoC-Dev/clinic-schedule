package com.clinic.schedule.portal.domain;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Collection;

import com.clinic.schedule.portal.domain.exception.WebBookingRefusedException;
import com.clinic.schedule.portal.domain.exception.WebBookingRefusedException.Reason;

/**
 * What a patient may book on the web (D-82), on top of what any booking must be — the appointment module's
 * rules, the same as reception's:
 *
 * <ul>
 *   <li>a reason reception lets patients book on the web;</li>
 *   <li>a day from tomorrow to {@code daysAhead} days on: today's appointments are reception's;</li>
 *   <li>one of the times the agenda offers that day, not any free time as reception may;</li>
 *   <li>at most {@value #MAX_AHEAD} appointments ahead per patient, every booked one counted, whoever booked
 *       it — {@value #MAX_AHEAD_UNTIL_VERIFIED} for a patient who registered on the web, until reception verifies
 *       who they are (D-91). The limit binds the web alone: reception books beyond it.</li>
 * </ul>
 *
 * <p>It is handed what it needs and reads nothing itself.
 */
public final class WebBookingPolicy {

    /** How many appointments ahead a patient may have and still book on the web. */
    static final int MAX_AHEAD = 3;

    /** How many a patient who registered on the web may have while reception has not verified them (D-91). */
    static final int MAX_AHEAD_UNTIL_VERIFIED = 1;

    private WebBookingPolicy() {
    }

    /** The first day a patient may book on the web: tomorrow. */
    public static LocalDate firstDay(LocalDate today) {
        return today.plusDays(1);
    }

    /** The last day a patient may book on the web. */
    public static LocalDate lastDay(LocalDate today, int daysAhead) {
        return today.plusDays(daysAhead);
    }

    /** Whether the day is one a patient may book on the web. */
    public static boolean isBookableDay(LocalDate day, LocalDate today, int daysAhead) {
        return !day.isBefore(firstDay(today)) && !day.isAfter(lastDay(today, daysAhead));
    }

    /**
     * @param reason the reason asked for; null when there is no such reason
     * @param offered the times the agenda offers that day for that reason
     * @param appointments every appointment of the patient, to count those still booked ahead
     * @param awaitsVerification whether the patient registered on the web and reception has not verified them
     * @throws WebBookingRefusedException when the patient may not book it on the web
     */
    public static void check(BookableReason reason, LocalDateTime startsAt, Collection<LocalTime> offered,
            Collection<Appointment> appointments, boolean awaitsVerification, LocalDateTime now, int daysAhead) {
        if (reason == null || !reason.webBookable()) {
            throw new WebBookingRefusedException(Reason.NOT_ON_THE_WEB, "That reason is not booked on the web");
        }
        if (!isBookableDay(startsAt.toLocalDate(), now.toLocalDate(), daysAhead)) {
            throw new WebBookingRefusedException(Reason.DAY_OUT_OF_RANGE,
                    "The web books from tomorrow to " + daysAhead + " days ahead");
        }
        if (!offered.contains(startsAt.toLocalTime())) {
            throw new WebBookingRefusedException(Reason.NOT_AN_OFFERED_TIME, "That time is not offered that day");
        }
        long ahead = appointments.stream()
                .filter(appointment -> appointment.state() == Appointment.State.BOOKED)
                .filter(appointment -> !appointment.startsAt().isBefore(now))
                .count();
        if (awaitsVerification && ahead >= MAX_AHEAD_UNTIL_VERIFIED) {
            throw new WebBookingRefusedException(Reason.ONE_AHEAD_UNTIL_VERIFIED, "A patient who registered on the "
                    + "web books " + MAX_AHEAD_UNTIL_VERIFIED + " ahead at most until reception verifies them");
        }
        if (ahead >= MAX_AHEAD) {
            throw new WebBookingRefusedException(Reason.TOO_MANY_AHEAD,
                    "A patient books on the web while they have fewer than " + MAX_AHEAD + " appointments ahead");
        }
    }
}
