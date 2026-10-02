package com.clinic.schedule.appointment.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;

import org.junit.jupiter.api.Test;

import com.clinic.schedule.appointment.domain.Appointment;
import com.clinic.schedule.appointment.domain.AppointmentId;
import com.clinic.schedule.appointment.domain.AppointmentStatus;
import com.clinic.schedule.appointment.domain.BookingChannel;
import com.clinic.schedule.appointment.domain.Cancellation;
import com.clinic.schedule.appointment.domain.CancellationRequester;
import com.clinic.schedule.appointment.domain.PatientId;
import com.clinic.schedule.appointment.domain.PractitionerId;
import com.clinic.schedule.appointment.domain.TimeSlot;

/** The list of cancellations: its days and its limit (D-76). */
class ListCancellationsUseCaseTest {

    private static final ZoneId ZONE = ZoneId.of("America/Argentina/Buenos_Aires");
    private static final LocalDate TODAY = LocalDate.of(2026, 3, 10);
    private static final Clock CLOCK = Clock.fixed(TODAY.atTime(12, 0).atZone(ZONE).toInstant(), ZONE);
    private static final PractitionerId ARIAS = PractitionerId.newId();

    private final InMemoryAppointmentRepository appointments = new InMemoryAppointmentRepository();
    private final ListCancellationsUseCase list = new ListCancellationsUseCase(appointments, CLOCK);

    private Appointment cancelledAt(LocalDateTime at) {
        Appointment appointment = Appointment.restore(AppointmentId.newId(), PatientId.newId(), ARIAS,
                TimeSlot.of(TODAY.plusDays(5).atTime(10, 0), 30), "consulta", null, AppointmentStatus.CANCELLED, false,
                Cancellation.byHand(at, "recepcion", CancellationRequester.PATIENT, null), BookingChannel.RECEPTION);
        appointments.given(appointment);
        return appointment;
    }

    @Test
    void withoutDaysItIsTheLastWeekUpToToday() {
        Appointment today = cancelledAt(TODAY.atTime(9, 0));
        Appointment sixDaysAgo = cancelledAt(TODAY.minusDays(6).atStartOfDay());
        cancelledAt(TODAY.minusDays(7).atTime(23, 59));

        ListCancellationsUseCase.Result result = list.handle(new ListCancellationsUseCase.Command(null, null, null));

        assertThat(result.from()).isEqualTo(TODAY.minusDays(6));
        assertThat(result.to()).isEqualTo(TODAY);
        assertThat(result.cancelled()).containsExactly(today, sixDaysAgo);
        assertThat(result.limitReached()).isFalse();
    }

    @Test
    void theLastDayIsIncludedWhole() {
        Appointment lateInTheDay = cancelledAt(TODAY.minusDays(1).atTime(23, 59));

        ListCancellationsUseCase.Result result = list.handle(new ListCancellationsUseCase.Command(null,
                TODAY.minusDays(1), TODAY.minusDays(1)));

        assertThat(result.cancelled()).containsExactly(lateInTheDay);
    }

    @Test
    void daysTheWrongWayRoundAreRefused() {
        assertThatIllegalArgumentException().isThrownBy(() -> list.handle(
                new ListCancellationsUseCase.Command(null, TODAY, TODAY.minusDays(1))));
    }

    @Test
    void atMostTheLimitComeBackAndTheAnswerSaysThereWereMore() {
        for (int i = 0; i < ListCancellationsUseCase.LIMIT; i++) {
            cancelledAt(TODAY.atTime(9, 0).plusSeconds(i));
        }

        assertThat(list.handle(new ListCancellationsUseCase.Command(null, null, null)).limitReached()).isFalse();

        cancelledAt(TODAY.atTime(8, 0));
        ListCancellationsUseCase.Result result = list.handle(new ListCancellationsUseCase.Command(null, null, null));

        assertThat(result.cancelled()).hasSize(ListCancellationsUseCase.LIMIT);
        assertThat(result.limitReached()).isTrue();
    }
}
