package com.clinic.schedule.portal.adapter.out.external;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.clinic.schedule.appointment.api.AppointmentReader;
import com.clinic.schedule.appointment.api.AppointmentWriter;
import com.clinic.schedule.appointment.api.BookingRefusedException;
import com.clinic.schedule.portal.application.port.BookingPort;
import com.clinic.schedule.portal.domain.exception.WebBookingRefusedException;
import com.clinic.schedule.portal.domain.exception.WebBookingRefusedException.Reason;

/**
 * Answers {@link BookingPort} through the appointment module's api (D-05), turning its refusals into this
 * module's: a time not bookable any more is, for the patient, a time taken.
 */
@Component
class BookingAdapter implements BookingPort {

    private final AppointmentReader reader;
    private final AppointmentWriter writer;

    BookingAdapter(AppointmentReader reader, AppointmentWriter writer) {
        this.reader = reader;
        this.writer = writer;
    }

    @Override
    public List<LocalTime> offeredTimes(UUID practitionerId, UUID reasonId, LocalDate date) {
        return reader.suggestedTimes(practitionerId, reasonId, date);
    }

    @Override
    public UUID book(UUID patientId, UUID practitionerId, UUID reasonId, LocalDateTime startsAt) {
        try {
            return writer.bookOnWeb(patientId, practitionerId, reasonId, startsAt);
        } catch (BookingRefusedException e) {
            throw new WebBookingRefusedException(
                    e.reason() == BookingRefusedException.Reason.PATIENT_BUSY ? Reason.PATIENT_BUSY : Reason.TIME_TAKEN,
                    e.getMessage());
        }
    }
}
