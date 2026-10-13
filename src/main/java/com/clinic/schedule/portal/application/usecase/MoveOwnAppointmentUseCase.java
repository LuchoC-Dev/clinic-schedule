package com.clinic.schedule.portal.application.usecase;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.clinic.schedule.portal.application.exception.NotYourAppointmentException;
import com.clinic.schedule.portal.application.port.AccountHolderPort;
import com.clinic.schedule.portal.application.port.AccountMailPort;
import com.clinic.schedule.portal.application.port.BookingPort;
import com.clinic.schedule.portal.application.port.CurrentPatientPort;
import com.clinic.schedule.portal.application.port.PatientAccountRepository;
import com.clinic.schedule.portal.application.port.PatientAppointmentsPort;
import com.clinic.schedule.portal.application.port.PractitionerNamePort;
import com.clinic.schedule.portal.domain.AccountHolder;
import com.clinic.schedule.portal.domain.Appointment;
import com.clinic.schedule.portal.domain.BookingConfirmation;
import com.clinic.schedule.portal.domain.PatientAccount;
import com.clinic.schedule.portal.domain.WebBookingPolicy;
import com.clinic.schedule.portal.domain.WebMovePolicy;
import com.clinic.schedule.portal.domain.exception.WebMoveRefusedException;

/**
 * The patient logged in moves one of their appointments to another time on the web (D-93), or one of a member
 * they act for (D-85): any booked, reception's too, up to {@code app.portal.cancel-hours-before} hours before
 * it, to a time the agenda offers from tomorrow to {@code app.portal.booking-days-ahead} days on, with the same
 * practitioner and reason. {@link WebMovePolicy} says whether they may; the appointment module moves it with
 * reception's rules, marked as moved on the web; and the account that moved it gets a confirmation by email,
 * naming the member it is for, with a new link to cancel it — the old one stops working, since it was for the
 * old time (D-83).
 *
 * <p>It is the same appointment: no limit of appointments ahead is counted, and it is not a cancellation. Its
 * reminder goes out again for the new time at the next day's run, as for any appointment moved (D-78).
 *
 * <p>One attempt at the email. A confirmation that does not go out leaves the appointment moved, and the answer
 * says so.
 */
@Service
public class MoveOwnAppointmentUseCase {

    private static final Logger log = LoggerFactory.getLogger(MoveOwnAppointmentUseCase.class);

    private final CurrentPatientPort currentPatient;
    private final PatientAppointmentsPort appointments;
    private final ViewFamilyUseCase family;
    private final BookingPort booking;
    private final PatientAccountRepository accounts;
    private final AccountHolderPort holders;
    private final PractitionerNamePort practitioners;
    private final AccountMailPort mail;
    private final CancelLinkUseCase cancelLinks;
    private final Clock clock;
    private final int daysAhead;
    private final int hoursBefore;

    public MoveOwnAppointmentUseCase(CurrentPatientPort currentPatient, PatientAppointmentsPort appointments,
            ViewFamilyUseCase family, BookingPort booking, PatientAccountRepository accounts,
            AccountHolderPort holders, PractitionerNamePort practitioners, AccountMailPort mail,
            CancelLinkUseCase cancelLinks, Clock clock, @Value("${app.portal.booking-days-ahead}") int daysAhead,
            @Value("${app.portal.cancel-hours-before}") int hoursBefore) {
        this.currentPatient = currentPatient;
        this.appointments = appointments;
        this.family = family;
        this.booking = booking;
        this.accounts = accounts;
        this.holders = holders;
        this.practitioners = practitioners;
        this.mail = mail;
        this.cancelLinks = cancelLinks;
        this.clock = clock;
        this.daysAhead = daysAhead;
        this.hoursBefore = hoursBefore;
    }

    /**
     * The appointment to move, whose it is, and the days it may be moved to.
     *
     * @throws NotYourAppointmentException when it is neither theirs nor of a member they act for
     */
    @Transactional(readOnly = true)
    public Target find(UUID appointmentId) {
        Owned owned = owned(appointmentId);
        LocalDateTime now = LocalDateTime.now(clock);
        return new Target(owned.appointment(), owned.member() == null ? null : owned.member().fullName(),
                WebMovePolicy.canMove(owned.appointment(), now, hoursBefore),
                WebBookingPolicy.firstDay(now.toLocalDate()), WebBookingPolicy.lastDay(now.toLocalDate(), daysAhead));
    }

    /**
     * The times it may be moved to on that day: those the agenda offers, but the one it already has.
     *
     * @throws NotYourAppointmentException when it is neither theirs nor of a member they act for
     * @throws WebMoveRefusedException when it may not be moved any more, or not to that day
     */
    @Transactional(readOnly = true)
    public List<LocalTime> times(UUID appointmentId, LocalDate date) {
        Appointment appointment = owned(appointmentId).appointment();
        LocalDateTime now = LocalDateTime.now(clock);
        WebMovePolicy.requireMovable(appointment, now, hoursBefore);
        WebMovePolicy.requireDay(date, now.toLocalDate(), daysAhead);
        return booking.movingTimes(appointmentId, date).stream()
                .filter(time -> !date.atTime(time).equals(appointment.startsAt()))
                .toList();
    }

    /**
     * @throws NotYourAppointmentException when it is neither theirs nor of a member they act for
     * @throws WebMoveRefusedException when it may not be moved there on the web
     */
    @Transactional
    public Result handle(UUID appointmentId, LocalDateTime startsAt) {
        Owned owned = owned(appointmentId);
        Appointment appointment = owned.appointment();
        WebMovePolicy.check(appointment, startsAt, booking.movingTimes(appointmentId, startsAt.toLocalDate()),
                LocalDateTime.now(clock), daysAhead, hoursBefore);
        booking.move(appointmentId, startsAt);
        String practitioner = practitioners.nameOf(appointment.practitionerId());
        String member = owned.member() == null ? null : owned.member().fullName();
        return new Result(appointmentId, owned.patientId(), startsAt, practitioner, member,
                confirm(member, appointmentId, startsAt, practitioner));
    }

    /** Theirs, or of a member they act for, and whose. */
    private Owned owned(UUID appointmentId) {
        UUID me = currentPatient.patientId();
        return appointments.of(me).stream()
                .filter(appointment -> appointment.id().equals(appointmentId))
                .findFirst()
                .map(appointment -> new Owned(appointment, me, null))
                .or(() -> family.actedFor().stream()
                        .flatMap(member -> appointments.of(member.patientId()).stream()
                                .filter(appointment -> appointment.id().equals(appointmentId))
                                .map(appointment -> new Owned(appointment, member.patientId(), member)))
                        .findFirst())
                .orElseThrow(() -> new NotYourAppointmentException(appointmentId));
    }

    /** To the account that moved it, whoever it is for, with the link to cancel it at its new time. */
    private boolean confirm(String member, UUID appointmentId, LocalDateTime startsAt, String practitioner) {
        UUID me = currentPatient.patientId();
        PatientAccount account = accounts.findByPatientId(me)
                .orElseThrow(() -> new IllegalStateException("No web account for the patient logged in"));
        String name = holders.find(me).map(AccountHolder::fullName)
                .orElseThrow(() -> new IllegalStateException("No patient for the web account logged in"));
        try {
            mail.sendBookingConfirmation(new BookingConfirmation(account.email(), name, member, startsAt,
                    practitioner, cancelLinks.issue(appointmentId, startsAt).orElse(null), true));
            return true;
        } catch (RuntimeException e) {
            log.warn("The confirmation of an appointment moved on the web did not go out: {}", e.getMessage());
            return false;
        }
    }

    /** @param member who it is for, when not the patient logged in; null otherwise */
    private record Owned(Appointment appointment, UUID patientId, AccountHolder member) {
    }

    /**
     * The appointment to move and whose it is.
     *
     * @param member the name of the member of their family it is for; null when it is their own
     * @param movable whether it may still be moved on the web
     * @param firstDay the first day it may be moved to
     * @param lastDay the last day it may be moved to
     */
    public record Target(Appointment appointment, String member, boolean movable, LocalDate firstDay,
            LocalDate lastDay) {
    }

    /**
     * The appointment moved, for whom, its new time, and whether its confirmation went out.
     *
     * @param member the name of the member of their family it is for; null when it is their own
     */
    public record Result(UUID appointmentId, UUID patientId, LocalDateTime startsAt, String practitioner,
            String member, boolean confirmationSent) {
    }
}
