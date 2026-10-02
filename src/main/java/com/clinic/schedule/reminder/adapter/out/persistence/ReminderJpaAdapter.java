package com.clinic.schedule.reminder.adapter.out.persistence;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Repository;

import com.clinic.schedule.reminder.application.port.ReminderRepository;
import com.clinic.schedule.reminder.domain.Reminder;

/** Implements the port over JPA, keeping a hash of the address and never the address (D-78). */
@Repository
class ReminderJpaAdapter implements ReminderRepository {

    private final ReminderRowRepository rows;

    ReminderJpaAdapter(ReminderRowRepository rows) {
        this.rows = rows;
    }

    @Override
    public void save(Reminder reminder) {
        rows.save(ReminderRow.from(reminder, reminder.address() == null ? null : AddressHash.of(reminder.address())));
    }

    @Override
    public List<Reminder> findForAppointments(Collection<UUID> appointmentIds) {
        return rows.findByAppointmentIdIn(appointmentIds).stream().map(ReminderRow::toReminder).toList();
    }

    @Override
    public List<Reminder> findForAppointmentsStartingBetween(LocalDateTime from, LocalDateTime until) {
        return rows.findByStartsAtGreaterThanEqualAndStartsAtLessThanOrderByStartsAtAscRecordedAtDesc(from, until)
                .stream()
                .map(ReminderRow::toReminder)
                .toList();
    }
}
