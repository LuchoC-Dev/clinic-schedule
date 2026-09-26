package com.clinic.schedule.appointment.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Import;

import com.clinic.schedule.appointment.application.port.AppointmentRepository;
import com.clinic.schedule.appointment.domain.Appointment;
import com.clinic.schedule.appointment.domain.AppointmentId;
import com.clinic.schedule.appointment.domain.AppointmentStatus;
import com.clinic.schedule.appointment.domain.PatientId;
import com.clinic.schedule.appointment.domain.PractitionerId;
import com.clinic.schedule.appointment.domain.TimeSlot;

/**
 * What only a real database can answer: that the mapping round-trips, that the queries
 * filter what they claim to, and that the Flyway schema matches the entity.
 */
@DataJpaTest
@Import(AppointmentJpaAdapter.class)
class AppointmentJpaAdapterIT {

    private static final LocalDate DAY = LocalDate.of(2026, 3, 10);
    private static final LocalDateTime TEN = DAY.atTime(10, 0);

    @Autowired
    private AppointmentRepository appointments;

    @Autowired
    private TestEntityManager entityManager;

    private final PatientId ana = PatientId.newId();
    private final PractitionerId arias = PractitionerId.newId();

    private Appointment appointment(PatientId patient, PractitionerId practitioner, LocalDateTime startsAt,
            int minutes, String note) {
        return Appointment.restore(AppointmentId.newId(), patient, practitioner,
                TimeSlot.of(startsAt, minutes), "consulta", note, AppointmentStatus.BOOKED, null, false);
    }

    /** Sends everything to the database and empties the context, so reads are real queries. */
    private void flush() {
        entityManager.flush();
        entityManager.clear();
    }

    @Test
    void storesAndReadsBackEveryField() {
        Appointment original = appointment(ana, arias, TEN, 45, "trae estudios");

        appointments.save(original);
        flush();

        Appointment found = appointments.findById(original.id()).orElseThrow();
        assertThat(found.id()).isEqualTo(original.id());
        assertThat(found.patientId()).isEqualTo(ana);
        assertThat(found.practitionerId()).isEqualTo(arias);
        assertThat(found.slot()).isEqualTo(TimeSlot.of(TEN, 45));
        assertThat(found.reason()).isEqualTo("consulta");
        assertThat(found.note()).isEqualTo("trae estudios");
        assertThat(found.status()).isEqualTo(AppointmentStatus.BOOKED);
    }

    @Test
    void anOptionalNoteComesBackAsNull() {
        Appointment original = appointment(ana, arias, TEN, 30, null);

        appointments.save(original);
        flush();

        assertThat(appointments.findById(original.id()).orElseThrow().note()).isNull();
    }

    @Test
    void anUnknownIdIsEmpty() {
        assertThat(appointments.findById(AppointmentId.newId())).isEmpty();
    }

    @Test
    void savingTheNewStateReplacesTheRowInsteadOfAddingOne() {
        Appointment original = appointment(ana, arias, TEN, 30, null);
        appointments.save(original);
        flush();

        appointments.save(original.cancelled());
        flush();

        assertThat(appointments.findById(original.id()).orElseThrow().status())
                .isEqualTo(AppointmentStatus.CANCELLED);
        assertThat(appointments.findBookedForPractitionerOn(arias, DAY)).isEmpty();
    }

    @Test
    void readsThePractitionersBookedDay() {
        appointments.save(appointment(ana, arias, TEN, 30, null));
        appointments.save(appointment(PatientId.newId(), arias, TEN.plusHours(2), 30, null));
        appointments.save(appointment(ana, PractitionerId.newId(), TEN.plusHours(4), 30, null));
        flush();

        assertThat(appointments.findBookedForPractitionerOn(arias, DAY)).hasSize(2);
    }

    @Test
    void readsThePatientsBookedDayAcrossPractitioners() {
        appointments.save(appointment(ana, arias, TEN, 30, null));
        appointments.save(appointment(ana, PractitionerId.newId(), TEN.plusHours(2), 30, null));
        flush();

        assertThat(appointments.findBookedForPatientOn(ana, DAY)).hasSize(2);
    }

    @Test
    void cancelledAppointmentsAreLeftOutOfTheDay() {
        appointments.save(appointment(ana, arias, TEN, 30, null).cancelled());
        flush();

        assertThat(appointments.findBookedForPractitionerOn(arias, DAY)).isEmpty();
        assertThat(appointments.findBookedForPatientOn(ana, DAY)).isEmpty();
    }

    @Test
    void storesTheCheckInAndTheLateArrivalNotice() {
        Appointment arrived = appointment(ana, arias, TEN, 30, null)
                .withLateArrivalNotice(TEN)
                .checkedIn(TEN.plusMinutes(40));

        appointments.save(arrived);
        flush();

        Appointment found = appointments.findById(arrived.id()).orElseThrow();
        assertThat(found.checkedInAt()).isEqualTo(TEN.plusMinutes(40));
        assertThat(found.hasLateArrivalNotice()).isTrue();
    }

    @Test
    void readsTheCandidatesForAutomaticCancellation() {
        Appointment startedEarlier = appointment(ana, arias, TEN.minusHours(1), 30, null);
        Appointment startsExactlyThen = appointment(PatientId.newId(), arias, TEN, 30, null);
        Appointment startsLater = appointment(PatientId.newId(), arias, TEN.plusMinutes(1), 30, null);
        Appointment arrived = appointment(PatientId.newId(), arias, TEN.minusHours(2), 30, null)
                .checkedIn(TEN.minusHours(2));
        Appointment cancelled = appointment(PatientId.newId(), arias, TEN.minusHours(3), 30, null).cancelled();
        for (Appointment appointment : new Appointment[] {startedEarlier, startsExactlyThen, startsLater, arrived, cancelled}) {
            appointments.save(appointment);
        }
        flush();

        assertThat(appointments.findBookedWithoutCheckInStartingBy(TEN))
                .extracting(Appointment::id)
                .containsExactlyInAnyOrder(startedEarlier.id(), startsExactlyThen.id());
    }

    @Test
    void theDayIsHalfOpenSoMidnightBelongsToTheNextOne() {
        appointments.save(appointment(ana, arias, DAY.atStartOfDay(), 30, null));
        appointments.save(appointment(ana, arias, DAY.plusDays(1).atStartOfDay(), 30, null));
        flush();

        assertThat(appointments.findBookedForPractitionerOn(arias, DAY)).hasSize(1);
        assertThat(appointments.findBookedForPractitionerOn(arias, DAY.plusDays(1))).hasSize(1);
    }

    @Test
    void aSpanOfDaysIncludesTheWholeOfItsLastDay() {
        LocalDate sunday = DAY.plusDays(5);
        Appointment first = appointment(ana, arias, DAY.atStartOfDay(), 30, null);
        Appointment last = appointment(ana, arias, sunday.atTime(23, 30), 30, null);
        appointments.save(first);
        appointments.save(last);
        appointments.save(appointment(ana, arias, DAY.minusDays(1).atTime(23, 30), 30, null));
        appointments.save(appointment(ana, arias, sunday.plusDays(1).atStartOfDay(), 30, null));
        appointments.save(appointment(ana, PractitionerId.newId(), TEN, 30, null));
        flush();

        assertThat(appointments.findNotCancelledForPractitionerBetween(arias, DAY, sunday))
                .extracting(Appointment::id)
                .containsExactlyInAnyOrder(first.id(), last.id());
    }

    @Test
    void readsEveryPractitionersBookedDaysWithoutCancelledOnes() {
        Appointment ariasAtTen = appointment(ana, arias, TEN, 30, null);
        Appointment someoneElseAtTen = appointment(PatientId.newId(), PractitionerId.newId(), TEN, 30, null);
        appointments.save(ariasAtTen);
        appointments.save(someoneElseAtTen);
        appointments.save(appointment(PatientId.newId(), arias, TEN.plusHours(1), 30, null).cancelled());
        appointments.save(appointment(ana, arias, TEN.plusDays(1), 30, null));
        flush();

        assertThat(appointments.findNotCancelledBetween(DAY, DAY))
                .extracting(Appointment::id)
                .containsExactlyInAnyOrder(ariasAtTen.id(), someoneElseAtTen.id());
    }

    @Test
    void readsEveryAppointmentOfThePatientCancelledOnesIncluded() {
        Appointment booked = appointment(ana, arias, TEN, 30, null);
        Appointment cancelled = appointment(ana, PractitionerId.newId(), TEN.minusMonths(2), 30, null).cancelled();
        appointments.save(booked);
        appointments.save(cancelled);
        appointments.save(appointment(PatientId.newId(), arias, TEN.plusHours(1), 30, null));
        flush();

        assertThat(appointments.findAllForPatient(ana))
                .extracting(Appointment::id)
                .containsExactlyInAnyOrder(booked.id(), cancelled.id());
    }
}
