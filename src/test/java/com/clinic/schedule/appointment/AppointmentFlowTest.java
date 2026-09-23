package com.clinic.schedule.appointment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDateTime;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import com.clinic.schedule.appointment.PractitionerFixture.CreatedPractitioner;
import com.clinic.schedule.appointment.application.AppointmentNotFoundException;
import com.clinic.schedule.appointment.application.BookAppointmentUseCase;
import com.clinic.schedule.appointment.application.CancelAppointmentUseCase;
import com.clinic.schedule.appointment.application.OutsideWorkingHoursException;
import com.clinic.schedule.appointment.application.RescheduleAppointmentUseCase;
import com.clinic.schedule.appointment.domain.Appointment;
import com.clinic.schedule.appointment.domain.AppointmentId;
import com.clinic.schedule.appointment.domain.AppointmentStatus;
import com.clinic.schedule.appointment.domain.PatientId;
import com.clinic.schedule.appointment.domain.PractitionerAlreadyBookedException;
import com.clinic.schedule.appointment.domain.PractitionerId;
import com.clinic.schedule.appointment.domain.TimeSlot;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * The flows end to end: wired by Spring, against the real schema, committing for real, and
 * asking the real practitioner module for reasons and hours.
 *
 * <p>Each test uses a fresh practitioner and fresh patients, so nothing has to be cleaned up
 * between them and they cannot interfere with each other.
 */
@SpringBootTest
@AutoConfigureMockMvc
class AppointmentFlowTest {

    /** A Tuesday. */
    private static final LocalDateTime TEN = LocalDateTime.of(2026, 3, 10, 10, 0);

    @Autowired
    private BookAppointmentUseCase book;

    @Autowired
    private RescheduleAppointmentUseCase reschedule;

    @Autowired
    private CancelAppointmentUseCase cancel;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper json;

    private PractitionerFixture fixture;

    @BeforeEach
    void setUp() {
        fixture = new PractitionerFixture(mockMvc, json);
    }

    private BookAppointmentUseCase.Command consulta(CreatedPractitioner practitioner, LocalDateTime startsAt) {
        return new BookAppointmentUseCase.Command(AppointmentId.newId(), PatientId.newId(),
                new PractitionerId(practitioner.id()), practitioner.consulta(), startsAt, null);
    }

    @Test
    void booksWithTheDurationConfiguredForTheReason() throws Exception {
        CreatedPractitioner arias = fixture.workingEveryDay();

        Appointment booked = book.handle(new BookAppointmentUseCase.Command(AppointmentId.newId(), PatientId.newId(),
                new PractitionerId(arias.id()), arias.ecografia(), TEN, null));

        assertThat(booked.slot()).isEqualTo(TimeSlot.of(TEN, 45));
        assertThat(booked.reason()).isEqualTo("ecografia");
    }

    @Test
    void refusesToBookOverAnAppointmentAlreadyInTheDatabase() throws Exception {
        CreatedPractitioner arias = fixture.workingEveryDay();
        book.handle(consulta(arias, TEN));

        assertThatThrownBy(() -> book.handle(consulta(arias, TEN.plusMinutes(15))))
                .isInstanceOf(PractitionerAlreadyBookedException.class);
    }

    @Test
    void refusesToBookDuringTheLunchBreak() throws Exception {
        CreatedPractitioner arias = fixture.workingTuesdaysWithALunchBreak();

        // 12:45-13:15 starts in the morning stretch and ends in the break.
        assertThatThrownBy(() -> book.handle(consulta(arias, TEN.withHour(12).withMinute(45))))
                .isInstanceOf(OutsideWorkingHoursException.class);
    }

    @Test
    void refusesToBookOnADayThePractitionerDoesNotWork() throws Exception {
        CreatedPractitioner arias = fixture.workingTuesdaysWithALunchBreak();

        assertThatThrownBy(() -> book.handle(consulta(arias, TEN.plusDays(1))))
                .isInstanceOf(OutsideWorkingHoursException.class);
    }

    @Test
    void aRetryWithTheSameIdReturnsTheStoredAppointment() throws Exception {
        BookAppointmentUseCase.Command sameRequest = consulta(fixture.workingEveryDay(), TEN);

        Appointment first = book.handle(sameRequest);
        Appointment retry = book.handle(sameRequest);

        assertThat(retry.isSameAs(first)).isTrue();
    }

    @Test
    void reschedulesKeepingTheDurationAndThenTheOldSlotIsFree() throws Exception {
        CreatedPractitioner arias = fixture.workingEveryDay();
        Appointment booked = book.handle(consulta(arias, TEN));

        Appointment moved = reschedule.handle(new RescheduleAppointmentUseCase.Command(booked.id(), TEN.plusHours(5)));

        assertThat(moved.slot()).isEqualTo(TimeSlot.of(TEN.plusHours(5), 30));
        assertThat(book.handle(consulta(arias, TEN)).isBooked()).isTrue();
    }

    @Test
    void refusesToRescheduleOutsideTheWorkingHours() throws Exception {
        CreatedPractitioner arias = fixture.workingTuesdaysWithALunchBreak();
        Appointment booked = book.handle(consulta(arias, TEN));

        assertThatThrownBy(() -> reschedule.handle(
                new RescheduleAppointmentUseCase.Command(booked.id(), TEN.withHour(19))))
                .isInstanceOf(OutsideWorkingHoursException.class);
    }

    @Test
    void cancellingFreesTheSlotAndKeepsTheRecord() throws Exception {
        CreatedPractitioner arias = fixture.workingEveryDay();
        Appointment booked = book.handle(consulta(arias, TEN));

        Appointment cancelled = cancel.handle(booked.id());

        assertThat(cancelled.status()).isEqualTo(AppointmentStatus.CANCELLED);
        assertThat(book.handle(consulta(arias, TEN)).isBooked()).isTrue();
    }

    @Test
    void anUnknownAppointmentCannotBeCancelled() {
        assertThatThrownBy(() -> cancel.handle(AppointmentId.newId()))
                .isInstanceOf(AppointmentNotFoundException.class);
    }
}
