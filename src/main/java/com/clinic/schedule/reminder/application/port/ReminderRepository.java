package com.clinic.schedule.reminder.application.port;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

import com.clinic.schedule.reminder.domain.Reminder;

/** Where the reminders are kept, each one recorded once and never changed (D-78). */
public interface ReminderRepository {

    void save(Reminder reminder);

    /** Every reminder of those appointments, whatever time they were about; never asked with none. */
    List<Reminder> findForAppointments(Collection<UUID> appointmentIds);

    /**
     * The reminders of the appointments from {@code from}, included, to {@code until}, excluded — by the
     * appointment's time —, in its order, and the latest first for each appointment.
     */
    List<Reminder> findForAppointmentsStartingBetween(LocalDateTime from, LocalDateTime until);
}
