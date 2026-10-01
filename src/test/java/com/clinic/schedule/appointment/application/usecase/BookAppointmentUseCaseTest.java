package com.clinic.schedule.appointment.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.ZoneId;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.clinic.schedule.appointment.application.exception.OutsideWorkingHoursException;
import com.clinic.schedule.appointment.application.exception.UnknownPatientException;
import com.clinic.schedule.appointment.application.exception.UnknownReasonException;
import com.clinic.schedule.appointment.domain.Appointment;
import com.clinic.schedule.appointment.domain.AppointmentId;
import com.clinic.schedule.appointment.domain.BookingService;
import com.clinic.schedule.appointment.domain.PatientId;
import com.clinic.schedule.appointment.domain.PractitionerId;
import com.clinic.schedule.appointment.domain.TimeSlot;
import com.clinic.schedule.appointment.domain.exception.PatientAlreadyBookedException;
import com.clinic.schedule.appointment.domain.exception.PractitionerAlreadyBookedException;
import com.clinic.schedule.appointment.domain.CancellationRequester;

class BookAppointmentUseCaseTest {

    private static final LocalDateTime TEN = LocalDateTime.of(2026, 3, 10, 10, 0);
    private static final PatientId ANA = PatientId.newId();
    private static final PatientId LUIS = PatientId.newId();
    private static final PractitionerId ARIAS = PractitionerId.newId();
    private static final PractitionerId BENITEZ = PractitionerId.newId();

    private final InMemoryAppointmentRepository appointments = new InMemoryAppointmentRepository();
    private final FakePatientRegistry patients = new FakePatientRegistry();
    private final FakePractitionerSchedule practitioners = new FakePractitionerSchedule();

    /** The practice's clock, stopped at the start of the day these tests book on. */
    private static final Clock CLOCK = Clock.fixed(
            LocalDateTime.of(2026, 3, 10, 0, 0).atZone(ZoneId.of("America/Argentina/Buenos_Aires")).toInstant(),
            ZoneId.of("America/Argentina/Buenos_Aires"));

    private final BookAppointmentUseCase useCase =
            new BookAppointmentUseCase(appointments, patients, practitioners, new BookingService(), CLOCK);

    {
        patients.register(ANA);
        patients.register(LUIS);
    }

    private final UUID ariasConsulta = practitioners.givenReason(ARIAS, "consulta", 30);
    private final UUID benitezConsulta = practitioners.givenReason(BENITEZ, "consulta", 30);

    private BookAppointmentUseCase.Command command(AppointmentId id, PatientId patient, PractitionerId practitioner,
            UUID reasonId, int atMinute) {
        return new BookAppointmentUseCase.Command(id, patient, practitioner, reasonId, TEN.plusMinutes(atMinute), null);
    }

    private BookAppointmentUseCase.Command withArias(PatientId patient, int atMinute) {
        return command(AppointmentId.newId(), patient, ARIAS, ariasConsulta, atMinute);
    }

    @Test
    void booksAndStoresTheAppointment() {
        AppointmentId id = AppointmentId.newId();

        Appointment booked = useCase.handle(command(id, ANA, ARIAS, ariasConsulta, 0));

        assertThat(booked.isBooked()).isTrue();
        assertThat(appointments.findById(id)).contains(booked);
    }

    @Test
    void theDurationAndTheNameComeFromTheReason() {
        UUID ecografia = practitioners.givenReason(ARIAS, "ecografia", 45);

        Appointment booked = useCase.handle(command(AppointmentId.newId(), ANA, ARIAS, ecografia, 0));

        assertThat(booked.slot()).isEqualTo(TimeSlot.of(TEN, 45));
        assertThat(booked.reason()).isEqualTo("ecografia");
    }

    @Test
    void anUnregisteredPatientIsRejectedAndNothingIsStored() {
        PatientId stranger = PatientId.newId();

        assertThatThrownBy(() -> useCase.handle(withArias(stranger, 0)))
                .isInstanceOf(UnknownPatientException.class);

        assertThat(appointments.storedCount()).isZero();
    }

    @Test
    void anUnknownReasonIsRejectedAndNothingIsStored() {
        assertThatThrownBy(() -> useCase.handle(command(AppointmentId.newId(), ANA, ARIAS, UUID.randomUUID(), 0)))
                .isInstanceOf(UnknownReasonException.class);

        assertThat(appointments.storedCount()).isZero();
    }

    @Test
    void anotherPractitionersReasonIsUnknownHere() {
        assertThatThrownBy(() -> useCase.handle(command(AppointmentId.newId(), ANA, ARIAS, benitezConsulta, 0)))
                .isInstanceOf(UnknownReasonException.class);
    }

    @Test
    void aSlotOutsideTheWorkingHoursIsRejectedAndNothingIsStored() {
        practitioners.worksOnly(ARIAS, TEN, TEN.plusHours(2));

        assertThatThrownBy(() -> useCase.handle(withArias(ANA, 120)))
                .isInstanceOf(OutsideWorkingHoursException.class);

        assertThat(appointments.storedCount()).isZero();
    }

    @Test
    void aSlotEndingExactlyWhenTheWorkingHoursEndIsAccepted() {
        practitioners.worksOnly(ARIAS, TEN, TEN.plusHours(2));

        Appointment last = useCase.handle(withArias(ANA, 90));

        assertThat(last.slot().endsAt()).isEqualTo(TEN.plusHours(2));
    }

    @Test
    void aSlotThatStartsInsideButRunsPastTheWorkingHoursIsRejected() {
        practitioners.worksOnly(ARIAS, TEN, TEN.plusHours(2));

        assertThatThrownBy(() -> useCase.handle(withArias(ANA, 100)))
                .isInstanceOf(OutsideWorkingHoursException.class);
    }

    @Test
    void aRetryWithTheSameIdDoesNotBookTwice() {
        AppointmentId id = AppointmentId.newId();
        BookAppointmentUseCase.Command sameRequest = command(id, ANA, ARIAS, ariasConsulta, 0);

        Appointment first = useCase.handle(sameRequest);
        Appointment retry = useCase.handle(sameRequest);

        assertThat(retry.isSameAs(first)).isTrue();
        assertThat(appointments.storedCount()).isEqualTo(1);
        assertThat(appointments.saveCount()).isEqualTo(1);
    }

    @Test
    void rejectsAnOverlapWithThePractitionersAgenda() {
        useCase.handle(withArias(LUIS, 0));

        assertThatThrownBy(() -> useCase.handle(withArias(ANA, 15)))
                .isInstanceOf(PractitionerAlreadyBookedException.class);

        assertThat(appointments.storedCount()).isEqualTo(1);
    }

    @Test
    void rejectsThePatientBeingBookedWithTwoPractitionersAtOnce() {
        useCase.handle(command(AppointmentId.newId(), ANA, BENITEZ, benitezConsulta, 0));

        assertThatThrownBy(() -> useCase.handle(withArias(ANA, 15)))
                .isInstanceOf(PatientAlreadyBookedException.class);
    }

    @Test
    void acceptsBackToBackAppointmentsForTheSamePractitioner() {
        useCase.handle(withArias(LUIS, 0));
        useCase.handle(withArias(ANA, 30));

        assertThat(appointments.storedCount()).isEqualTo(2);
    }

    @Test
    void aCancelledAppointmentDoesNotBlockItsSlot() {
        AppointmentId first = AppointmentId.newId();
        useCase.handle(command(first, LUIS, ARIAS, ariasConsulta, 0));
        new CancelAppointmentUseCase(appointments, new FakeArrivals(), () -> "recepcion", CLOCK)
                .handle(new CancelAppointmentUseCase.Command(first, CancellationRequester.PATIENT, null));

        Appointment second = useCase.handle(withArias(ANA, 0));

        assertThat(second.isBooked()).isTrue();
    }

    @Test
    void anotherDayAtTheSameTimeIsFree() {
        useCase.handle(withArias(LUIS, 0));

        Appointment nextDay = useCase.handle(new BookAppointmentUseCase.Command(AppointmentId.newId(), ANA, ARIAS,
                ariasConsulta, TEN.plusDays(1), null));

        assertThat(nextDay.isBooked()).isTrue();
    }
}
