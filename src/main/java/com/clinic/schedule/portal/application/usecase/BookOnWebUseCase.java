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
 * The patient logged in books an appointment for themselves on the web (D-82). {@link WebBookingPolicy}
 * says what the web lets them book; the appointment module books it with reception's rules, marked as
 * booked on the web; and the account that booked it gets a confirmation by email, with the link to cancel it
 * (D-83).
 *
 * <p>One attempt at the email. A confirmation that does not go out leaves the appointment booked, and the
 * answer says so.
 */
@Service
public class BookOnWebUseCase {

    private static final Logger log = LoggerFactory.getLogger(BookOnWebUseCase.class);

    private final CurrentPatientPort currentPatient;
    private final BookableReasonsPort reasons;
    private final BookingPort booking;
    private final PatientAppointmentsPort appointments;
    private final PatientAccountRepository accounts;
    private final AccountHolderPort holders;
    private final AccountMailPort mail;
    private final CancelLinkUseCase cancelLinks;
    private final Clock clock;
    private final int daysAhead;

    public BookOnWebUseCase(CurrentPatientPort currentPatient, BookableReasonsPort reasons, BookingPort booking,
            PatientAppointmentsPort appointments, PatientAccountRepository accounts, AccountHolderPort holders,
            AccountMailPort mail, CancelLinkUseCase cancelLinks, Clock clock,
            @Value("${app.portal.booking-days-ahead}") int daysAhead) {
        this.currentPatient = currentPatient;
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

    /** @throws com.clinic.schedule.portal.domain.exception.WebBookingRefusedException when it may not be booked */
    @Transactional
    public Result handle(UUID practitionerId, UUID reasonId, LocalDateTime startsAt) {
        UUID patientId = currentPatient.patientId();
        LocalDateTime now = LocalDateTime.now(clock);
        BookableReason reason = reasons.all().stream()
                .filter(candidate -> candidate.practitionerId().equals(practitionerId)
                        && candidate.reasonId().equals(reasonId))
                .findFirst()
                .orElse(null);
        List<LocalTime> offered = reason == null ? List.of()
                : booking.offeredTimes(practitionerId, reasonId, startsAt.toLocalDate());
        WebBookingPolicy.check(reason, startsAt, offered, appointments.of(patientId), now, daysAhead);
        UUID appointmentId = booking.book(patientId, practitionerId, reasonId, startsAt);
        return new Result(appointmentId, startsAt, reason.practitionerName(),
                confirm(patientId, appointmentId, startsAt, reason));
    }

    private boolean confirm(UUID patientId, UUID appointmentId, LocalDateTime startsAt, BookableReason reason) {
        PatientAccount account = accounts.findByPatientId(patientId)
                .orElseThrow(() -> new IllegalStateException("No web account for the patient logged in"));
        String name = holders.find(patientId).map(AccountHolder::fullName)
                .orElseThrow(() -> new IllegalStateException("No patient for the web account logged in"));
        try {
            mail.sendBookingConfirmation(new BookingConfirmation(account.email(), name, startsAt,
                    reason.practitionerName(), cancelLinks.issue(appointmentId, startsAt).orElse(null)));
            return true;
        } catch (RuntimeException e) {
            log.warn("The confirmation of an appointment booked on the web did not go out: {}", e.getMessage());
            return false;
        }
    }

    /** The appointment booked, and whether its confirmation went out. */
    public record Result(UUID appointmentId, LocalDateTime startsAt, String practitioner, boolean confirmationSent) {
    }
}
