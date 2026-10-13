package com.clinic.schedule.appointment.adapter.in.external;

import java.time.LocalDateTime;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.clinic.schedule.appointment.api.AppointmentWriter;
import com.clinic.schedule.appointment.api.BookingRefusedException;
import com.clinic.schedule.appointment.api.BookingRefusedException.Reason;
import com.clinic.schedule.appointment.api.CancellationRefusedException;
import com.clinic.schedule.appointment.application.exception.AppointmentNotFoundException;
import com.clinic.schedule.appointment.application.exception.OutsideWorkingHoursException;
import com.clinic.schedule.appointment.application.exception.UnknownPatientException;
import com.clinic.schedule.appointment.application.exception.UnknownReasonException;
import com.clinic.schedule.appointment.application.usecase.BookAppointmentUseCase;
import com.clinic.schedule.appointment.application.usecase.CancelForPatientUseCase;
import com.clinic.schedule.appointment.application.usecase.RescheduleAppointmentUseCase;
import com.clinic.schedule.appointment.domain.AppointmentId;
import com.clinic.schedule.appointment.domain.BookingChannel;
import com.clinic.schedule.appointment.domain.PatientId;
import com.clinic.schedule.appointment.domain.PractitionerId;
import com.clinic.schedule.appointment.domain.exception.BookingRuleViolationException;
import com.clinic.schedule.appointment.domain.exception.PatientAlreadyBookedException;
import com.clinic.schedule.appointment.domain.exception.PractitionerAlreadyBookedException;
import com.clinic.schedule.appointment.domain.exception.SlotInThePastException;
import com.clinic.schedule.appointment.domain.exception.SlotTooFarAheadException;

/**
 * Answers this module's {@link AppointmentWriter} for other modules, as {@link AppointmentReaderAdapter}
 * answers the reader (D-48): it books and moves through the same use cases as reception, and turns this module's
 * refusals into the api's own.
 */
@Component
class AppointmentWriterAdapter implements AppointmentWriter {

    private final BookAppointmentUseCase book;
    private final RescheduleAppointmentUseCase reschedule;
    private final CancelForPatientUseCase cancel;

    AppointmentWriterAdapter(BookAppointmentUseCase book, RescheduleAppointmentUseCase reschedule,
            CancelForPatientUseCase cancel) {
        this.book = book;
        this.reschedule = reschedule;
        this.cancel = cancel;
    }

    @Override
    public UUID bookOnWeb(UUID patientId, UUID practitionerId, UUID reasonId, LocalDateTime startsAt) {
        try {
            return book.handle(new BookAppointmentUseCase.Command(AppointmentId.newId(), new PatientId(patientId),
                    new PractitionerId(practitionerId), reasonId, startsAt, null, BookingChannel.WEB)).id().value();
        } catch (PractitionerAlreadyBookedException e) {
            throw new BookingRefusedException(Reason.TIME_TAKEN, e.getMessage());
        } catch (PatientAlreadyBookedException e) {
            throw new BookingRefusedException(Reason.PATIENT_BUSY, e.getMessage());
        } catch (OutsideWorkingHoursException | SlotInThePastException | SlotTooFarAheadException
                | UnknownReasonException | UnknownPatientException e) {
            throw new BookingRefusedException(Reason.NOT_BOOKABLE, e.getMessage());
        }
    }

    @Override
    public void moveOnWeb(UUID appointmentId, LocalDateTime startsAt) {
        try {
            reschedule.handle(new RescheduleAppointmentUseCase.Command(new AppointmentId(appointmentId), startsAt,
                    BookingChannel.WEB));
        } catch (PractitionerAlreadyBookedException e) {
            throw new BookingRefusedException(Reason.TIME_TAKEN, e.getMessage());
        } catch (PatientAlreadyBookedException e) {
            throw new BookingRefusedException(Reason.PATIENT_BUSY, e.getMessage());
        } catch (AppointmentNotFoundException | BookingRuleViolationException e) {
            throw new BookingRefusedException(Reason.NOT_BOOKABLE, e.getMessage());
        }
    }

    @Override
    public void cancelOnWeb(UUID appointmentId) {
        try {
            cancel.onTheWeb(new AppointmentId(appointmentId));
        } catch (AppointmentNotFoundException | BookingRuleViolationException e) {
            throw new CancellationRefusedException(e.getMessage());
        }
    }

    @Override
    public void cancelFromEmailLink(UUID appointmentId) {
        try {
            cancel.byEmailLink(new AppointmentId(appointmentId));
        } catch (AppointmentNotFoundException | BookingRuleViolationException e) {
            throw new CancellationRefusedException(e.getMessage());
        }
    }
}
