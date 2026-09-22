package com.clinic.schedule.appointment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import com.clinic.schedule.appointment.application.AppointmentNotFoundException;
import com.clinic.schedule.appointment.application.BookAppointmentUseCase;
import com.clinic.schedule.appointment.application.CancelAppointmentUseCase;
import com.clinic.schedule.appointment.application.RescheduleAppointmentUseCase;
import com.clinic.schedule.appointment.domain.Appointment;
import com.clinic.schedule.appointment.domain.AppointmentId;
import com.clinic.schedule.appointment.domain.AppointmentStatus;
import com.clinic.schedule.appointment.domain.PatientId;
import com.clinic.schedule.appointment.domain.PractitionerAlreadyBookedException;
import com.clinic.schedule.appointment.domain.PractitionerId;
import com.clinic.schedule.appointment.domain.TimeSlot;

/**
 * The flows end to end: wired by Spring, against the real schema, committing for real.
 *
 * <p>Each test uses fresh ids, so nothing has to be cleaned up between them and they cannot
 * interfere with each other.
 */
@SpringBootTest
class AppointmentFlowTest {

    private static final LocalDateTime TEN = LocalDateTime.of(2026, 3, 10, 10, 0);

    @Autowired
    private BookAppointmentUseCase book;

    @Autowired
    private RescheduleAppointmentUseCase reschedule;

    @Autowired
    private CancelAppointmentUseCase cancel;

    private BookAppointmentUseCase.Command booking(PatientId patient, PractitionerId practitioner,
            LocalDateTime startsAt, int minutes) {
        return new BookAppointmentUseCase.Command(AppointmentId.newId(), patient, practitioner, startsAt, minutes,
                "consulta", null);
    }

    @Test
    void booksAnAppointmentThatCanBeReadBackAfterwards() {
        Appointment booked = book.handle(booking(PatientId.newId(), PractitionerId.newId(), TEN, 30));

        Appointment reread = cancel.handle(booked.id());
        assertThat(reread.id()).isEqualTo(booked.id());
        assertThat(reread.slot()).isEqualTo(TimeSlot.of(TEN, 30));
    }

    @Test
    void refusesToBookOverAnAppointmentAlreadyInTheDatabase() {
        PractitionerId arias = PractitionerId.newId();
        book.handle(booking(PatientId.newId(), arias, TEN, 30));

        assertThatThrownBy(() -> book.handle(booking(PatientId.newId(), arias, TEN.plusMinutes(15), 30)))
                .isInstanceOf(PractitionerAlreadyBookedException.class);
    }

    @Test
    void aRetryWithTheSameIdReturnsTheStoredAppointment() {
        BookAppointmentUseCase.Command sameRequest =
                booking(PatientId.newId(), PractitionerId.newId(), TEN, 30);

        Appointment first = book.handle(sameRequest);
        Appointment retry = book.handle(sameRequest);

        assertThat(retry.isSameAs(first)).isTrue();
    }

    @Test
    void reschedulesAndThenTheOldSlotIsFree() {
        PractitionerId arias = PractitionerId.newId();
        Appointment booked = book.handle(booking(PatientId.newId(), arias, TEN, 30));

        reschedule.handle(new RescheduleAppointmentUseCase.Command(booked.id(), TEN.plusHours(5), 45));

        Appointment onTheOldSlot = book.handle(booking(PatientId.newId(), arias, TEN, 30));
        assertThat(onTheOldSlot.isBooked()).isTrue();
    }

    @Test
    void cancellingFreesTheSlotAndKeepsTheRecord() {
        PractitionerId arias = PractitionerId.newId();
        Appointment booked = book.handle(booking(PatientId.newId(), arias, TEN, 30));

        Appointment cancelled = cancel.handle(booked.id());

        assertThat(cancelled.status()).isEqualTo(AppointmentStatus.CANCELLED);
        assertThat(book.handle(booking(PatientId.newId(), arias, TEN, 30)).isBooked()).isTrue();
    }

    @Test
    void anUnknownAppointmentCannotBeCancelled() {
        assertThatThrownBy(() -> cancel.handle(AppointmentId.newId()))
                .isInstanceOf(AppointmentNotFoundException.class);
    }
}
