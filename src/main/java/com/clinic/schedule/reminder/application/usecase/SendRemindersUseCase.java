package com.clinic.schedule.reminder.application.usecase;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.clinic.schedule.reminder.application.port.BookedAppointmentsPort;
import com.clinic.schedule.reminder.application.port.PatientContactPort;
import com.clinic.schedule.reminder.application.port.PractitionerNamesPort;
import com.clinic.schedule.reminder.application.port.ReminderRepository;
import com.clinic.schedule.reminder.application.port.ReminderSenderPort;
import com.clinic.schedule.reminder.domain.BookedAppointment;
import com.clinic.schedule.reminder.domain.Contact;
import com.clinic.schedule.reminder.domain.Reminder;
import com.clinic.schedule.reminder.domain.ReminderMessage;
import com.clinic.schedule.reminder.domain.ReminderPolicy;

/**
 * Reminds the patients of their appointments of the next days (D-78): every appointment still booked
 * from tomorrow to {@code daysAhead} days on that has not been reminded yet of its time. Today's are not:
 * a reminder the same day says nothing reception could not say.
 *
 * <p>Each one is decided by {@link ReminderPolicy}, sent once, and recorded — sent, failed, or not sent
 * and why. One sent or failed is not tried again for that time; one moved afterwards is reminded of its
 * new time. One not sent is decided again the next day, so a consent recorded in between counts.
 */
@Service
public class SendRemindersUseCase {

    private static final Logger log = LoggerFactory.getLogger(SendRemindersUseCase.class);

    private final BookedAppointmentsPort appointments;
    private final PatientContactPort patients;
    private final PractitionerNamesPort practitioners;
    private final ReminderRepository reminders;
    private final ReminderSenderPort sender;
    private final Clock clock;
    private final int daysAhead;

    public SendRemindersUseCase(BookedAppointmentsPort appointments, PatientContactPort patients,
            PractitionerNamesPort practitioners, ReminderRepository reminders, ReminderSenderPort sender, Clock clock,
            @Value("${app.reminders.days-ahead}") int daysAhead) {
        if (daysAhead < 1) {
            throw new IllegalArgumentException("Reminders go out at least a day ahead");
        }
        this.appointments = appointments;
        this.patients = patients;
        this.practitioners = practitioners;
        this.reminders = reminders;
        this.sender = sender;
        this.clock = clock;
        this.daysAhead = daysAhead;
    }

    /** @return what was recorded, one for each appointment considered */
    @Transactional
    public List<Reminder> handle() {
        LocalDateTime now = LocalDateTime.now(clock);
        LocalDate today = now.toLocalDate();
        List<BookedAppointment> booked = appointments.between(today.plusDays(1), today.plusDays(daysAhead));
        if (booked.isEmpty()) {
            return List.of();
        }
        List<Reminder> earlier = reminders.findForAppointments(booked.stream().map(BookedAppointment::id).toList());
        List<BookedAppointment> due = booked.stream()
                .filter(appointment -> earlier.stream().noneMatch(reminder -> reminder.settles(appointment)))
                .toList();
        Map<UUID, Contact> contacts = patients.contactsOf(due.stream().map(BookedAppointment::patientId).toList());
        Map<UUID, String> names = practitioners.names();

        List<Reminder> recorded = new ArrayList<>();
        for (BookedAppointment appointment : due) {
            Reminder reminder = remind(appointment, contacts.getOrDefault(appointment.patientId(), Contact.UNKNOWN),
                    names.get(appointment.practitionerId()), now);
            reminders.save(reminder);
            recorded.add(reminder);
        }
        return recorded;
    }

    private Reminder remind(BookedAppointment appointment, Contact contact, String practitioner, LocalDateTime now) {
        ReminderPolicy.Decision decision = ReminderPolicy.decide(contact, appointment.startsAt().toLocalDate());
        if (decision.notSent() != null) {
            return Reminder.notSent(appointment, decision.notSent(), now);
        }
        try {
            sender.send(new ReminderMessage(decision.recipient(), appointment.startsAt(), practitioner));
            return Reminder.sent(appointment, decision.recipient().address(), now);
        } catch (RuntimeException e) {
            log.warn("The reminder of appointment {} did not go out: {}", appointment.id(), e.getMessage());
            return Reminder.failed(appointment, decision.recipient().address(), now);
        }
    }
}
