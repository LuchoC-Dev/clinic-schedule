package com.clinic.schedule.portal.application.usecase;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.clinic.schedule.portal.application.port.AppointmentCancellationPort;
import com.clinic.schedule.portal.application.port.CancelLinkRepository;
import com.clinic.schedule.portal.domain.BookedAppointment;
import com.clinic.schedule.portal.domain.CancelLink;
import com.clinic.schedule.portal.domain.SecretToken;
import com.clinic.schedule.portal.domain.WebCancellationPolicy;
import com.clinic.schedule.portal.domain.exception.WebCancellationRefusedException;
import com.clinic.schedule.portal.domain.exception.WebCancellationRefusedException.Reason;

/**
 * The links to cancel an appointment from an email (D-83): issued for its reminders and for the confirmation
 * of a web booking, read by whoever opens one, and used to cancel it, with nobody logged in. A link works
 * while its appointment is still booked at the time it was sent for, and up to
 * {@code app.portal.cancel-hours-before} hours before it, as on the web.
 */
@Service
public class CancelLinkUseCase {

    private final CancelLinkRepository links;
    private final AppointmentCancellationPort appointments;
    private final Clock clock;
    private final int hoursBefore;

    public CancelLinkUseCase(CancelLinkRepository links, AppointmentCancellationPort appointments, Clock clock,
            @Value("${app.portal.cancel-hours-before}") int hoursBefore) {
        this.links = links;
        this.appointments = appointments;
        this.clock = clock;
        this.hoursBefore = hoursBefore;
    }

    /**
     * A new link for the appointment at that time, or none when there is no time left to cancel it on the web:
     * the email then says to call.
     *
     * @return the token, for the link
     */
    @Transactional
    public Optional<String> issue(UUID appointmentId, LocalDateTime startsAt) {
        if (!WebCancellationPolicy.isInTime(startsAt, LocalDateTime.now(clock), hoursBefore)) {
            return Optional.empty();
        }
        CancelLink.Issued issued = CancelLink.issue(appointmentId, startsAt);
        links.save(issued.stored());
        return Optional.of(issued.token());
    }

    /**
     * The appointment the link would cancel.
     *
     * @throws WebCancellationRefusedException when it leads to no appointment booked at that time, or there is
     *         no time left
     */
    @Transactional(readOnly = true)
    public BookedAppointment read(String token) {
        BookedAppointment appointment = linked(token);
        if (!WebCancellationPolicy.isInTime(appointment.startsAt(), LocalDateTime.now(clock), hoursBefore)) {
            throw new WebCancellationRefusedException(Reason.TOO_LATE,
                    "An appointment is cancelled on the web up to " + hoursBefore + " hours before it");
        }
        return appointment;
    }

    /** @throws WebCancellationRefusedException as {@link #read} does */
    @Transactional
    public BookedAppointment cancel(String token) {
        BookedAppointment appointment = read(token);
        appointments.cancelFromEmailLink(appointment.id());
        return appointment;
    }

    private BookedAppointment linked(String token) {
        Optional<CancelLink> link = token == null || token.isBlank() ? Optional.empty()
                : links.findByHash(SecretToken.hashOf(token));
        return link.flatMap(found -> appointments.findBooked(found.appointmentId()).filter(found::isFor))
                .orElseThrow(() -> new WebCancellationRefusedException(Reason.GONE,
                        "The link leads to no appointment booked at the time it was sent for"));
    }
}
