package com.clinic.schedule.appointment.persistence;

import java.time.LocalDateTime;
import java.util.UUID;

import com.clinic.schedule.appointment.domain.Appointment;
import com.clinic.schedule.appointment.domain.AppointmentId;
import com.clinic.schedule.appointment.domain.AppointmentStatus;
import com.clinic.schedule.appointment.domain.PatientId;
import com.clinic.schedule.appointment.domain.PractitionerId;
import com.clinic.schedule.appointment.domain.TimeSlot;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * The appointment as a row. Separate from the aggregate (D-16), which is why this class can
 * have the no-argument constructor and the mutable fields JPA needs while
 * {@link Appointment} stays immutable.
 *
 * <p>The translation lives here, next to the columns it knows about. Note that the row keeps
 * the duration as an {@code int} of minutes, which is portable and easy to query, while the
 * domain works with a {@code Duration}.
 */
@Entity
@Table(name = "appointment")
class AppointmentRow {

    @Id
    private UUID id;

    @Column(name = "patient_id", nullable = false)
    private UUID patientId;

    @Column(name = "practitioner_id", nullable = false)
    private UUID practitionerId;

    @Column(name = "starts_at", nullable = false)
    private LocalDateTime startsAt;

    @Column(name = "duration_minutes", nullable = false)
    private int durationMinutes;

    @Column(nullable = false)
    private String reason;

    private String note;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AppointmentStatus status;

    protected AppointmentRow() {
        // required by JPA
    }

    static AppointmentRow from(Appointment appointment) {
        AppointmentRow row = new AppointmentRow();
        row.id = appointment.id().value();
        row.patientId = appointment.patientId().value();
        row.practitionerId = appointment.practitionerId().value();
        row.startsAt = appointment.slot().startsAt();
        row.durationMinutes = (int) appointment.slot().duration().toMinutes();
        row.reason = appointment.reason();
        row.note = appointment.note();
        row.status = appointment.status();
        return row;
    }

    Appointment toAppointment() {
        return Appointment.restore(
                new AppointmentId(id),
                new PatientId(patientId),
                new PractitionerId(practitionerId),
                TimeSlot.of(startsAt, durationMinutes),
                reason,
                note,
                status);
    }
}
