package com.clinic.schedule.appointment.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.clinic.schedule.appointment.application.port.AppointmentRepository;
import com.clinic.schedule.appointment.domain.Appointment;
import com.clinic.schedule.appointment.domain.AppointmentId;
import com.clinic.schedule.appointment.domain.AppointmentStatus;
import com.clinic.schedule.appointment.domain.PatientId;
import com.clinic.schedule.appointment.domain.PractitionerId;
import com.clinic.schedule.appointment.domain.TimeSlot;
import com.clinic.schedule.appointment.domain.exception.PatientAlreadyBookedException;
import com.clinic.schedule.appointment.domain.exception.PractitionerAlreadyBookedException;
import com.clinic.schedule.appointment.domain.Cancellations;

/**
 * What only PostgreSQL can answer: that the database itself refuses overlapping appointments,
 * and that the adapter answers the refusal as the application's own (D-21, D-44).
 *
 * <p>The appointments are stored through the adapter, past the application's check — which is
 * exactly what two receptionists booking the same slot at once amount to: both pass the check,
 * and only the database is left to say no. Doing it this way is deterministic; racing two
 * threads would pass or fail with the timing.
 *
 * <p>It also runs every migration against a real PostgreSQL. Skipped when Docker is not
 * running, so the build never depends on it.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(AppointmentJpaAdapter.class)
@Testcontainers(disabledWithoutDocker = true)
class AppointmentOverlapPostgresIT {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:18-alpine");

    @DynamicPropertySource
    static void postgres(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    private static final LocalDateTime TEN = LocalDateTime.of(2026, 3, 10, 10, 0);

    @Autowired
    private AppointmentRepository appointments;

    private final PractitionerId arias = PractitionerId.newId();
    private final PatientId ana = PatientId.newId();

    private static Appointment booked(PatientId patient, PractitionerId practitioner, LocalDateTime startsAt) {
        return Appointment.restore(AppointmentId.newId(), patient, practitioner, TimeSlot.of(startsAt, 30),
                "consulta", null, AppointmentStatus.BOOKED, false, null);
    }

    @Test
    void thePractitionerCannotBeBookedTwiceForTheSameTime() {
        appointments.save(booked(ana, arias, TEN));

        assertThatThrownBy(() -> appointments.save(booked(PatientId.newId(), arias, TEN.plusMinutes(15))))
                .isInstanceOf(PractitionerAlreadyBookedException.class);
    }

    @Test
    void thePatientCannotBeBookedTwiceForTheSameTimeEvenWithAnotherPractitioner() {
        appointments.save(booked(ana, arias, TEN));

        assertThatThrownBy(() -> appointments.save(booked(ana, PractitionerId.newId(), TEN.plusMinutes(15))))
                .isInstanceOf(PatientAlreadyBookedException.class);
    }

    @Test
    void backToBackAppointmentsDoNotCollide() {
        appointments.save(booked(ana, arias, TEN));

        assertThatNoException().isThrownBy(() -> appointments.save(booked(ana, arias, TEN.plusMinutes(30))));
    }

    @Test
    void aCancelledAppointmentFreesItsSlot() {
        Appointment first = booked(ana, arias, TEN);
        appointments.save(first);
        appointments.save(first.cancelled(Cancellations.byHand()));

        assertThatNoException().isThrownBy(() -> appointments.save(booked(ana, arias, TEN)));
    }
}
