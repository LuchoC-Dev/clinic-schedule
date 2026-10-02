package com.clinic.schedule.appointment.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Import;

import jakarta.persistence.PersistenceException;

import com.clinic.schedule.appointment.application.port.AppointmentRepository;
import com.clinic.schedule.appointment.domain.Appointment;
import com.clinic.schedule.appointment.domain.AppointmentDayService;
import com.clinic.schedule.appointment.domain.AppointmentId;
import com.clinic.schedule.appointment.domain.AppointmentStatus;
import com.clinic.schedule.appointment.domain.BookingChannel;
import com.clinic.schedule.appointment.domain.Cancellation;
import com.clinic.schedule.appointment.domain.CancellationRequester;
import com.clinic.schedule.appointment.domain.Cancellations;
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
                TimeSlot.of(startsAt, minutes), "consulta", note, AppointmentStatus.BOOKED, false, null,
                        BookingChannel.RECEPTION);
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
    void aCancellationRoundTrips() {
        Appointment original = appointment(ana, arias, TEN, 30, null);
        Cancellation byHand = Cancellation.byHand(TEN.minusHours(2), "recepcion", CancellationRequester.PATIENT,
                "Viaja por trabajo");
        Appointment timeOff = appointment(PatientId.newId(), arias, TEN.plusHours(1), 30, null);

        appointments.save(original.cancelled(byHand));
        appointments.save(timeOff.cancelled(Cancellation.byTimeOff(TEN.minusHours(1), "recepcion")));
        flush();

        assertThat(appointments.findById(original.id()).orElseThrow().cancellation()).contains(byHand);
        assertThat(appointments.findById(timeOff.id()).orElseThrow().cancellation())
                .contains(Cancellation.byTimeOff(TEN.minusHours(1), "recepcion"));
    }

    @Test
    void theCancellationsAreFoundByWhenTheyWereMadeTheMostRecentFirst() {
        Appointment earlier = appointment(PatientId.newId(), arias, TEN, 30, null);
        Appointment later = appointment(PatientId.newId(), arias, TEN.plusHours(1), 30, null);
        Appointment tomorrow = appointment(PatientId.newId(), arias, TEN.plusHours(2), 30, null);
        Appointment someoneElses = appointment(PatientId.newId(), PractitionerId.newId(), TEN, 30, null);
        appointments.save(earlier.cancelled(Cancellation.byHand(DAY.atTime(8, 0), "recepcion",
                CancellationRequester.PATIENT, null)));
        appointments.save(later.cancelled(Cancellation.byHand(DAY.atTime(9, 0), "recepcion",
                CancellationRequester.PATIENT, null)));
        appointments.save(tomorrow.cancelled(Cancellation.byHand(DAY.plusDays(1).atStartOfDay(), "recepcion",
                CancellationRequester.PATIENT, null)));
        appointments.save(someoneElses.cancelled(Cancellations.byHand()));
        appointments.save(appointment(PatientId.newId(), arias, TEN.plusHours(3), 30, null));
        flush();

        assertThat(appointments.findCancelledBetween(arias, DAY.atStartOfDay(), DAY.plusDays(1).atStartOfDay(), 10))
                .extracting(Appointment::id)
                .containsExactly(later.id(), earlier.id());
        assertThat(appointments.findCancelledBetween(arias, DAY.atStartOfDay(), DAY.plusDays(1).atStartOfDay(), 1))
                .extracting(Appointment::id)
                .containsExactly(later.id());
    }

    @Test
    void theDatabaseKeepsACancellationWholeAndOnlyOnACancelledAppointment() {
        Appointment booked = appointment(ana, arias, TEN, 30, null);
        appointments.save(booked);
        flush();

        assertThatThrownBy(() -> update("update appointment set cancelled_at = timestamp '2026-03-10 09:00:00', "
                + "cancelled_by = 'recepcion', cancellation_origin = 'BY_HAND', cancellation_requested_by = 'PATIENT' "
                + "where id = ?", booked))
                .isInstanceOf(PersistenceException.class);
        assertThatThrownBy(() -> update("update appointment set status = 'CANCELLED', "
                + "cancelled_at = timestamp '2026-03-10 09:00:00', cancelled_by = 'recepcion', "
                + "cancellation_origin = 'BY_HAND' where id = ?", booked))
                .isInstanceOf(PersistenceException.class);
        assertThatThrownBy(() -> update("update appointment set status = 'CANCELLED', "
                + "cancelled_at = timestamp '2026-03-10 09:00:00', cancelled_by = 'recepcion', "
                + "cancellation_origin = 'TIME_OFF', cancellation_requested_by = 'PATIENT' where id = ?", booked))
                .isInstanceOf(PersistenceException.class);
    }

    private void update(String sql, Appointment appointment) {
        entityManager.getEntityManager().createNativeQuery(sql).setParameter(1, appointment.id().value())
                .executeUpdate();
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

        appointments.save(original.cancelled(Cancellations.byHand()));
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
        appointments.save(appointment(ana, arias, TEN, 30, null).cancelled(Cancellations.byHand()));
        flush();

        assertThat(appointments.findBookedForPractitionerOn(arias, DAY)).isEmpty();
        assertThat(appointments.findBookedForPatientOn(ana, DAY)).isEmpty();
    }

    @Test
    void storesTheLateArrivalNotice() {
        Appointment warned = new AppointmentDayService().lateArrivalNotice(appointment(ana, arias, TEN, 30, null), TEN,
                Optional.empty());

        appointments.save(warned);
        flush();

        assertThat(appointments.findById(warned.id()).orElseThrow().hasLateArrivalNotice()).isTrue();
    }

    @Test
    void readsTheCandidatesToBeMarkedAbsent() {
        Appointment startedEarlier = appointment(ana, arias, TEN.minusHours(1), 30, null);
        Appointment startsExactlyThen = appointment(PatientId.newId(), arias, TEN, 30, null);
        Appointment startsLater = appointment(PatientId.newId(), arias, TEN.plusMinutes(1), 30, null);
        Appointment cancelled = appointment(PatientId.newId(), arias, TEN.minusHours(3), 30, null)
                        .cancelled(Cancellations.byHand());
        for (Appointment appointment : new Appointment[] {startedEarlier, startsExactlyThen, startsLater, cancelled}) {
            appointments.save(appointment);
        }
        flush();

        // Whose patient arrived is the arrivals' to say, not the query's (D-65).
        assertThat(appointments.findBookedStartingBy(TEN))
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
        appointments.save(appointment(PatientId.newId(), arias, TEN.plusHours(1), 30, null)
                        .cancelled(Cancellations.byHand()));
        appointments.save(appointment(ana, arias, TEN.plusDays(1), 30, null));
        flush();

        assertThat(appointments.findNotCancelledBetween(DAY, DAY))
                .extracting(Appointment::id)
                .containsExactlyInAnyOrder(ariasAtTen.id(), someoneElseAtTen.id());
    }

    @Test
    void readsEveryAppointmentOfThePatientCancelledOnesIncluded() {
        Appointment booked = appointment(ana, arias, TEN, 30, null);
        Appointment cancelled = appointment(ana, PractitionerId.newId(), TEN.minusMonths(2), 30, null)
                        .cancelled(Cancellations.byHand());
        appointments.save(booked);
        appointments.save(cancelled);
        appointments.save(appointment(PatientId.newId(), arias, TEN.plusHours(1), 30, null));
        flush();

        assertThat(appointments.findAllForPatient(ana))
                .extracting(Appointment::id)
                .containsExactlyInAnyOrder(booked.id(), cancelled.id());
    }
}
