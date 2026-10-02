package com.clinic.schedule.reminder.application.usecase;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.clinic.schedule.reminder.application.port.ReminderRepository;
import com.clinic.schedule.reminder.domain.Reminder;

/**
 * The reminders of the appointments from one day to another, by the appointment's day (D-78): what
 * reception looks at to call the patients a reminder did not reach. Who may read it is the route's to
 * say — reception alone (D-34).
 *
 * <p>Without days, from today to as far ahead as reminders go.
 */
@Service
public class ListRemindersUseCase {

    private final ReminderRepository reminders;
    private final Clock clock;
    private final int daysAhead;

    public ListRemindersUseCase(ReminderRepository reminders, Clock clock,
            @Value("${app.reminders.days-ahead}") int daysAhead) {
        this.reminders = reminders;
        this.clock = clock;
        this.daysAhead = daysAhead;
    }

    /** @throws IllegalArgumentException when the first day comes after the last */
    @Transactional(readOnly = true)
    public Result handle(LocalDate from, LocalDate to) {
        LocalDate first = from != null ? from : LocalDate.now(clock);
        LocalDate last = to != null ? to : first.plusDays(daysAhead);
        if (first.isAfter(last)) {
            throw new IllegalArgumentException("The first day comes after the last");
        }
        return new Result(reminders.findForAppointmentsStartingBetween(first.atStartOfDay(),
                last.plusDays(1).atStartOfDay()), first, last);
    }

    /** The reminders found, and the days searched. */
    public record Result(List<Reminder> reminders, LocalDate from, LocalDate to) {
    }
}
