package com.clinic.schedule.appointment.adapter.out.persistence;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Repository;

import com.clinic.schedule.appointment.application.port.AppointmentRepository;
import com.clinic.schedule.appointment.domain.Appointment;
import com.clinic.schedule.appointment.domain.AppointmentId;
import com.clinic.schedule.appointment.domain.AppointmentStatus;
import com.clinic.schedule.appointment.domain.PatientId;
import com.clinic.schedule.appointment.domain.PractitionerId;
import com.clinic.schedule.appointment.domain.exception.PatientAlreadyBookedException;
import com.clinic.schedule.appointment.domain.exception.PractitionerAlreadyBookedException;

/** Implements the port over JPA. The only place that knows appointments live in a table. */
@Repository
class AppointmentJpaAdapter implements AppointmentRepository {

    /** The exclusion constraints of V7, PostgreSQL only (D-44). */
    static final String PRACTITIONER_OVERLAP = "ex_appointment_practitioner_overlap";
    static final String PATIENT_OVERLAP = "ex_appointment_patient_overlap";

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
     *
     * <p>It flushes at once, so that PostgreSQL's overlap constraints answer here rather than at
     * commit, where nothing could translate them. When two bookings of the same slot race, the
     * application's check lets both through and the database refuses the second; that refusal
     * leaves as the one the application would have given, so the client still gets a 409
     * (D-21, D-44).
     */
    @Override
    public void save(Appointment appointment) {
        try {
            rows.saveAndFlush(AppointmentRow.from(appointment));
        } catch (DataIntegrityViolationException e) {
            if (violates(e, PRACTITIONER_OVERLAP)) {
                throw new PractitionerAlreadyBookedException(appointment.practitionerId(), appointment.slot());
            }
            if (violates(e, PATIENT_OVERLAP)) {
                throw new PatientAlreadyBookedException(appointment.patientId(), appointment.slot());
            }
            throw e;
        }
    }

    /**
     * Whether the database named that constraint. Read from the driver's message, which always
     * carries the name, because Hibernate does not extract it for exclusion constraints.
     */
    private static boolean violates(DataIntegrityViolationException e, String constraint) {
        for (Throwable cause = e; cause != null; cause = cause.getCause()) {
            if (cause.getMessage() != null && cause.getMessage().contains(constraint)) {
                return true;
            }
        }
        return false;
    }

    private List<Appointment> toAppointments(List<AppointmentRow> found) {
        return found.stream().map(AppointmentRow::toAppointment).toList();
    }

    private LocalDateTime startOf(LocalDate date) {
        return date.atStartOfDay();
    }
}
