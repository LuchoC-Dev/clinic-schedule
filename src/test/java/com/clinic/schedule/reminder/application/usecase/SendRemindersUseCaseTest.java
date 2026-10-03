package com.clinic.schedule.reminder.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.clinic.schedule.reminder.application.port.ReminderRepository;
import com.clinic.schedule.reminder.domain.BookedAppointment;
import com.clinic.schedule.reminder.domain.Consent;
import com.clinic.schedule.reminder.domain.Contact;
import com.clinic.schedule.reminder.domain.NotSentReason;
import com.clinic.schedule.reminder.domain.Reminder;
import com.clinic.schedule.reminder.domain.ReminderMessage;
import com.clinic.schedule.reminder.domain.ReminderOutcome;

/** Which appointments are reminded each day, once, and what is recorded of each (D-78). */
class SendRemindersUseCaseTest {

    private static final ZoneId ZONE = ZoneId.of("America/Argentina/Buenos_Aires");
    private static final LocalDate TODAY = LocalDate.of(2026, 3, 10);
    private static final Clock CLOCK = Clock.fixed(TODAY.atTime(10, 0).atZone(ZONE).toInstant(), ZONE);
    private static final UUID ARIAS = UUID.randomUUID();

    private final List<BookedAppointment> booked = new ArrayList<>();
    private final Map<UUID, Contact> contacts = new HashMap<>();
    private final List<Reminder> stored = new ArrayList<>();
    private final List<ReminderMessage> sent = new ArrayList<>();
    private boolean senderFails;
    private LocalDate askedFrom;
    private LocalDate askedTo;

    private final ReminderRepository repository = new ReminderRepository() {
        @Override
        public void save(Reminder reminder) {
            stored.add(reminder);
        }

        @Override
        public List<Reminder> findForAppointments(Collection<UUID> appointmentIds) {
            return stored.stream().filter(reminder -> appointmentIds.contains(reminder.appointmentId())).toList();
        }

        @Override
        public List<Reminder> findForAppointmentsStartingBetween(LocalDateTime from, LocalDateTime until) {
            throw new UnsupportedOperationException();
        }
    };

    private final SendRemindersUseCase sendReminders = new SendRemindersUseCase(
            (from, to) -> {
                askedFrom = from;
                askedTo = to;
                return List.copyOf(booked);
            },
            patientIds -> contacts,
            () -> Map.of(ARIAS, "Dra. Laura Arias"),
            repository,
            message -> {
                if (senderFails) {
                    throw new IllegalStateException("The SMTP server did not answer");
                }
                sent.add(message);
            },
            (appointmentId, startsAt) -> Optional.of("https://turnos/cancel/" + appointmentId),
            CLOCK, 3);

    private BookedAppointment booked(LocalDateTime startsAt, Contact contact) {
        BookedAppointment appointment = new BookedAppointment(UUID.randomUUID(), UUID.randomUUID(), ARIAS, startsAt);
        booked.add(appointment);
        contacts.put(appointment.patientId(), contact);
        return appointment;
    }

    private static Contact consenting(String name) {
        return new Contact(name, null, "patient@mail.com", null, Consent.PATIENT);
    }

    @Test
    void itAsksForTheAppointmentsFromTomorrowToTheDaysAhead() {
        sendReminders.handle();

        assertThat(askedFrom).isEqualTo(TODAY.plusDays(1));
        assertThat(askedTo).isEqualTo(TODAY.plusDays(3));
    }

    @Test
    void eachAppointmentIsSentWithWhenAndWithWhomAndRecorded() {
        BookedAppointment appointment = booked(TODAY.plusDays(2).atTime(11, 0), consenting("Luis Pérez"));

        List<Reminder> recorded = sendReminders.handle();

        assertThat(sent).singleElement().satisfies(message -> {
            assertThat(message.startsAt()).isEqualTo(appointment.startsAt());
            assertThat(message.practitioner()).isEqualTo("Dra. Laura Arias");
            assertThat(message.recipient().address()).isEqualTo("patient@mail.com");
            assertThat(message.recipient().greeted()).isEqualTo("Luis Pérez");
            assertThat(message.cancelLink()).isEqualTo("https://turnos/cancel/" + appointment.id());
        });
        assertThat(stored).isEqualTo(recorded).singleElement().satisfies(reminder -> {
            assertThat(reminder.outcome()).isEqualTo(ReminderOutcome.SENT);
            assertThat(reminder.appointmentId()).isEqualTo(appointment.id());
            assertThat(reminder.startsAt()).isEqualTo(appointment.startsAt());
            assertThat(reminder.address()).isEqualTo("patient@mail.com");
            assertThat(reminder.recordedAt()).isEqualTo(TODAY.atTime(10, 0));
        });
    }

    @Test
    void oneSentOrFailedIsNotTriedAgainForTheSameTime() {
        booked(TODAY.plusDays(1).atTime(11, 0), consenting("Luis Pérez"));
        BookedAppointment failing = booked(TODAY.plusDays(1).atTime(12, 0), consenting("Ana Gómez"));
        sendReminders.handle();
        stored.replaceAll(reminder -> reminder.appointmentId().equals(failing.id())
                ? Reminder.failed(failing, "patient@mail.com", reminder.recordedAt()) : reminder);
        sent.clear();

        assertThat(sendReminders.handle()).isEmpty();
        assertThat(sent).isEmpty();
    }

    @Test
    void oneMovedAfterItsReminderIsRemindedOfItsNewTime() {
        BookedAppointment appointment = booked(TODAY.plusDays(1).atTime(11, 0), consenting("Luis Pérez"));
        sendReminders.handle();
        booked.set(0, new BookedAppointment(appointment.id(), appointment.patientId(), ARIAS,
                TODAY.plusDays(2).atTime(9, 0)));

        sendReminders.handle();

        assertThat(sent).extracting(ReminderMessage::startsAt)
                .containsExactly(TODAY.plusDays(1).atTime(11, 0), TODAY.plusDays(2).atTime(9, 0));
    }

    @Test
    void oneNotSentIsDecidedAgainTheNextTime() {
        BookedAppointment appointment = booked(TODAY.plusDays(2).atTime(11, 0),
                new Contact("Luis Pérez", null, "patient@mail.com", null, null));
        sendReminders.handle();
        contacts.put(appointment.patientId(), consenting("Luis Pérez"));

        sendReminders.handle();

        assertThat(stored).extracting(Reminder::outcome)
                .containsExactly(ReminderOutcome.NOT_SENT, ReminderOutcome.SENT);
        assertThat(stored.get(0).reason()).isEqualTo(NotSentReason.NO_CONSENT);
    }

    @Test
    void aPatientTheOtherModuleDoesNotKnowHasNoEmail() {
        booked(TODAY.plusDays(1).atTime(11, 0), consenting("Luis Pérez"));
        contacts.clear();

        assertThat(sendReminders.handle()).singleElement()
                .satisfies(reminder -> assertThat(reminder.reason()).isEqualTo(NotSentReason.NO_EMAIL));
    }

    @Test
    void aSendingThatFailsIsRecordedAsFailedAndTheRestGoOn() {
        booked(TODAY.plusDays(1).atTime(11, 0), consenting("Luis Pérez"));
        booked(TODAY.plusDays(1).atTime(12, 0), new Contact("Ana Gómez", null, null, null, null));
        senderFails = true;

        List<Reminder> recorded = sendReminders.handle();

        assertThat(recorded).extracting(Reminder::outcome)
                .containsExactly(ReminderOutcome.FAILED, ReminderOutcome.NOT_SENT);
        assertThat(recorded.get(0).address()).isEqualTo("patient@mail.com");
    }

    @Test
    void theAgeThatCountsIsTheOneOnTheDayOfTheVisit() {
        // Twelve today, thirteen on the day of the appointment, on her mother's email with only her consent.
        LocalDateTime startsAt = TODAY.plusDays(2).atTime(11, 0);
        booked(startsAt, new Contact("Sofía Pérez", startsAt.toLocalDate().minusYears(13), "ana@mail.com",
                "Ana Pérez", Consent.OWNER));

        assertThat(sendReminders.handle()).singleElement()
                .satisfies(reminder -> assertThat(reminder.reason()).isEqualTo(NotSentReason.CONFIDENTIAL));
    }

    @Test
    void withNothingBookedNothingElseIsAsked() {
        ReminderRepository untouchable = new ReminderRepository() {
            @Override
            public void save(Reminder reminder) {
                throw new AssertionError("nothing to save");
            }

            @Override
            public List<Reminder> findForAppointments(Collection<UUID> appointmentIds) {
                throw new AssertionError("nothing to look for");
            }

            @Override
            public List<Reminder> findForAppointmentsStartingBetween(LocalDateTime from, LocalDateTime until) {
                throw new AssertionError("nothing to list");
            }
        };

        assertThat(new SendRemindersUseCase((from, to) -> List.of(), ids -> {
            throw new AssertionError("no patient to ask for");
        }, Map::of, untouchable, message -> { }, (id, at) -> Optional.empty(), CLOCK, 3).handle()).isEmpty();
    }

    @Test
    void remindersGoAtLeastADayAhead() {
        assertThatIllegalArgumentException().isThrownBy(() -> new SendRemindersUseCase(
                (from, to) -> List.of(), ids -> Map.of(), Map::of, repository, message -> { },
                (id, at) -> Optional.empty(), CLOCK, 0));
    }
}
