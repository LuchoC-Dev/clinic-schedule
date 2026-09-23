package com.clinic.schedule.appointment.persistence;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Repository;

import com.clinic.schedule.appointment.application.AppointmentRepository;
import com.clinic.schedule.appointment.domain.Appointment;
import com.clinic.schedule.appointment.domain.AppointmentId;
import com.clinic.schedule.appointment.domain.AppointmentStatus;
import com.clinic.schedule.appointment.domain.PatientId;
import com.clinic.schedule.appointment.domain.PractitionerId;

/** Implements the port over JPA. The only place that knows appointments live in a table. */
@Repository
class AppointmentJpaAdapter implements AppointmentRepository {

    private final AppointmentRowRepository rows;

    AppointmentJpaAdapter(AppointmentRowRepository rows) {
        this.rows = rows;
    }

    @Override
    public Optional<Appointment> findById(AppointmentId id) {
        return rows.findById(id.value()).map(AppointmentRow::toAppointment);
    }

    @Override
    public List<Appointment> findBookedForPractitionerOn(PractitionerId practitionerId, LocalDate date) {
        return toAppointments(rows.findByPractitionerIdAndStatusAndStartsAtGreaterThanEqualAndStartsAtLessThan(
                practitionerId.value(), AppointmentStatus.BOOKED, startOf(date), startOf(date.plusDays(1))));
    }

    @Override
    public List<Appointment> findBookedForPatientOn(PatientId patientId, LocalDate date) {
        return toAppointments(rows.findByPatientIdAndStatusAndStartsAtGreaterThanEqualAndStartsAtLessThan(
                patientId.value(), AppointmentStatus.BOOKED, startOf(date), startOf(date.plusDays(1))));
    }

    @Override
    public List<Appointment> findBookedForPractitionerBetween(PractitionerId practitionerId, LocalDate from,
            LocalDate to) {
        return toAppointments(rows.findByPractitionerIdAndStatusAndStartsAtGreaterThanEqualAndStartsAtLessThan(
                practitionerId.value(), AppointmentStatus.BOOKED, startOf(from), startOf(to.plusDays(1))));
    }

    @Override
    public List<Appointment> findBookedBetween(LocalDate from, LocalDate to) {
        return toAppointments(rows.findByStatusAndStartsAtGreaterThanEqualAndStartsAtLessThan(
                AppointmentStatus.BOOKED, startOf(from), startOf(to.plusDays(1))));
    }

    @Override
    public List<Appointment> findAllForPatient(PatientId patientId) {
        return toAppointments(rows.findByPatientId(patientId.value()));
    }

    @Override
    public List<Appointment> findBookedWithoutCheckInStartingBy(LocalDateTime instant) {
        return toAppointments(rows.findByStatusAndCheckedInAtIsNullAndStartsAtLessThanEqual(
                AppointmentStatus.BOOKED, instant));
    }

    /**
     * Writes the appointment as it is now. Since the aggregate is immutable and carries its
     * own id, storing a new one and storing the new state of an existing one are the same
     * operation.
     */
    @Override
    public void save(Appointment appointment) {
        rows.save(AppointmentRow.from(appointment));
    }

    private List<Appointment> toAppointments(List<AppointmentRow> found) {
        return found.stream().map(AppointmentRow::toAppointment).toList();
    }

    private LocalDateTime startOf(LocalDate date) {
        return date.atStartOfDay();
    }
}
