package com.clinic.schedule.portal.adapter.out.external;

import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.clinic.schedule.appointment.api.AppointmentReader;
import com.clinic.schedule.appointment.api.AppointmentWriter;
import com.clinic.schedule.appointment.api.CancellationRefusedException;
import com.clinic.schedule.portal.application.port.AppointmentCancellationPort;
import com.clinic.schedule.portal.domain.BookedAppointment;
import com.clinic.schedule.portal.domain.exception.WebCancellationRefusedException;
import com.clinic.schedule.portal.domain.exception.WebCancellationRefusedException.Reason;

/** Answers {@link AppointmentCancellationPort} through the appointment module's api (D-05), translating its refusal. */
@Component
class AppointmentCancellationAdapter implements AppointmentCancellationPort {

    private final AppointmentReader reader;
    private final AppointmentWriter writer;

    AppointmentCancellationAdapter(AppointmentReader reader, AppointmentWriter writer) {
        this.reader = reader;
        this.writer = writer;
    }

    @Override
    public Optional<BookedAppointment> findBooked(UUID appointmentId) {
        return reader.findBooked(appointmentId).map(appointment -> new BookedAppointment(appointment.id(),
                appointment.patientId(), appointment.practitionerId(), appointment.startsAt()));
    }

    @Override
    public void cancelOnWeb(UUID appointmentId) {
        try {
            writer.cancelOnWeb(appointmentId);
        } catch (CancellationRefusedException e) {
            throw new WebCancellationRefusedException(Reason.NOT_BOOKED, e.getMessage());
        }
    }

    @Override
    public void cancelFromEmailLink(UUID appointmentId) {
        try {
            writer.cancelFromEmailLink(appointmentId);
        } catch (CancellationRefusedException e) {
            throw new WebCancellationRefusedException(Reason.NOT_BOOKED, e.getMessage());
        }
    }
}
