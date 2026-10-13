package com.clinic.schedule.portal.application.usecase;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.clinic.schedule.portal.application.port.AccountHolderPort;
import com.clinic.schedule.portal.application.port.AccountMailPort;
import com.clinic.schedule.portal.application.port.BookableReasonsPort;
import com.clinic.schedule.portal.application.port.BookingPort;
import com.clinic.schedule.portal.application.port.CurrentPatientPort;
import com.clinic.schedule.portal.application.port.PatientAccountRepository;
import com.clinic.schedule.portal.application.port.PatientAppointmentsPort;
import com.clinic.schedule.portal.domain.AccountHolder;
import com.clinic.schedule.portal.domain.BookableReason;
import com.clinic.schedule.portal.domain.BookingConfirmation;
import com.clinic.schedule.portal.domain.PatientAccount;
import com.clinic.schedule.portal.domain.WebBookingPolicy;

/**
 * The patient logged in books an appointment on the web (D-82), for themselves or for a member of their
 * family they act for (D-85). {@link WebBookingPolicy} says what the web lets them book, counting the
 * appointments of whom it is for; the appointment module books it with reception's rules, marked as booked on
 * the web; and the account that booked it gets a confirmation by email, naming the member it is for, with the
 * link to cancel it (D-83).
 *
 * <p>One attempt at the email. A confirmation that does not go out leaves the appointment booked, and the
 * answer says so.
 */
@Service
public class BookOnWebUseCase {

    private static final Logger log = LoggerFactory.getLogger(BookOnWebUseCase.class);

    private final CurrentPatientPort currentPatient;
    private final ViewFamilyUseCase family;
    private final BookableReasonsPort reasons;
    private final BookingPort booking;
    private final PatientAppointmentsPort appointments;
    private final PatientAccountRepository accounts;
    private final AccountHolderPort holders;
    private final AccountMailPort mail;
    private final CancelLinkUseCase cancelLinks;
    private final Clock clock;
    private final int daysAhead;

    public BookOnWebUseCase(CurrentPatientPort currentPatient, ViewFamilyUseCase family, BookableReasonsPort reasons,
            BookingPort booking,
            PatientAppointmentsPort appointments, PatientAccountRepository accounts, AccountHolderPort holders,
            AccountMailPort mail, CancelLinkUseCase cancelLinks, Clock clock,
            @Value("${app.portal.booking-days-ahead}") int daysAhead) {
        this.currentPatient = currentPatient;
        this.family = family;
        this.reasons = reasons;
        this.booking = booking;
        this.appointments = appointments;
        this.accounts = accounts;
        this.holders = holders;
        this.mail = mail;
        this.cancelLinks = cancelLinks;
        this.clock = clock;
        this.daysAhead = daysAhead;
    }

    /**
     * @param forPatientId a member of their family they act for (D-85); null for the patient themselves
     * @throws com.clinic.schedule.portal.application.exception.NotYourFamilyMemberException when they do not act
     *         for that patient
     * @throws com.clinic.schedule.portal.domain.exception.WebBookingRefusedException when it may not be booked
     */
    @Transactional
    public Result handle(UUID forPatientId, UUID practitionerId, UUID reasonId, LocalDateTime startsAt) {
        UUID me = currentPatient.patientId();
        AccountHolder member = forPatientId == null || forPatientId.equals(me) ? null
                : family.requireActedFor(forPatientId);
        AccountHolder bookedFor = member != null ? member : holders.find(me)
                .orElseThrow(() -> new IllegalStateException("No patient for the web account logged in"));
        UUID patientId = bookedFor.patientId();
        LocalDateTime now = LocalDateTime.now(clock);
        BookableReason reason = reasons.all().stream()
                .filter(candidate -> candidate.practitionerId().equals(practitionerId)
                        && candidate.reasonId().equals(reasonId))
                .findFirst()
                .orElse(null);
        List<LocalTime> offered = reason == null ? List.of()
                : booking.offeredTimes(practitionerId, reasonId, startsAt.toLocalDate());
        WebBookingPolicy.check(reason, startsAt, offered, appointments.of(patientId), bookedFor.awaitsVerification(),
                now, daysAhead);
        UUID appointmentId = booking.book(patientId, practitionerId, reasonId, startsAt);
        return new Result(appointmentId, patientId, startsAt, reason.practitionerName(),
                member == null ? null : member.fullName(), confirm(me, member, appointmentId, startsAt, reason));
    }

    /** To the account that booked it, whoever it is for. */
    private boolean confirm(UUID me, AccountHolder member, UUID appointmentId, LocalDateTime startsAt,
            BookableReason reason) {
        PatientAccount account = accounts.findByPatientId(me)
                .orElseThrow(() -> new IllegalStateException("No web account for the patient logged in"));
        String name = holders.find(me).map(AccountHolder::fullName)
                .orElseThrow(() -> new IllegalStateException("No patient for the web account logged in"));
        try {
            mail.sendBookingConfirmation(new BookingConfirmation(account.email(), name,
                    member == null ? null : member.fullName(), startsAt, reason.practitionerName(),
                    cancelLinks.issue(appointmentId, startsAt).orElse(null), false));
            return true;
        } catch (RuntimeException e) {
            log.warn("The confirmation of an appointment booked on the web did not go out: {}", e.getMessage());
            return false;
        }
    }

    /**
     * The appointment booked, for whom, and whether its confirmation went out.
     *
     * @param member the name of the member of their family it was booked for; null when it is their own
     */
    public record Result(UUID appointmentId, UUID patientId, LocalDateTime startsAt, String practitioner,
            String member, boolean confirmationSent) {
    }
}
