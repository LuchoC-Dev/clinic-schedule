package com.clinic.schedule.reminder.adapter.out.persistence;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

/** Spring Data over {@link ReminderRow}; the application talks to {@code ReminderRepository}, the port. */
interface ReminderRowRepository extends JpaRepository<ReminderRow, UUID> {

    List<ReminderRow> findByAppointmentIdIn(Collection<UUID> appointmentIds);

    List<ReminderRow> findByStartsAtGreaterThanEqualAndStartsAtLessThanOrderByStartsAtAscRecordedAtDesc(
            LocalDateTime from, LocalDateTime until);
}
