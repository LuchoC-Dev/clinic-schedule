package com.clinic.schedule.appointment.adapter.out.persistence;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import com.clinic.schedule.appointment.domain.AppointmentStatus;

/**
 * Spring Data over {@link AppointmentRow}. A detail of this package: the application talks
 * to {@code AppointmentRepository}, which is the port, and never to this interface.
 *
 * <p>The day is expressed as a half-open range — from midnight, up to but not including the
 * next one — so no appointment is counted twice or missed.
 *
 * <p>Specifications for the list of cancellations, whose practitioner applies only when it is given.
 */
interface AppointmentRowRepository extends JpaRepository<AppointmentRow, UUID>,
        JpaSpecificationExecutor<AppointmentRow> {

    List<AppointmentRow> findByPractitionerIdAndStatusAndStartsAtGreaterThanEqualAndStartsAtLessThan(
            UUID practitionerId, AppointmentStatus status, LocalDateTime from, LocalDateTime until);

    List<AppointmentRow> findByPatientIdAndStatusAndStartsAtGreaterThanEqualAndStartsAtLessThan(
            UUID patientId, AppointmentStatus status, LocalDateTime from, LocalDateTime until);

    List<AppointmentRow> findByStatusAndStartsAtGreaterThanEqualAndStartsAtLessThan(
            AppointmentStatus status, LocalDateTime from, LocalDateTime until);

    List<AppointmentRow> findByPractitionerIdAndStatusNotAndStartsAtGreaterThanEqualAndStartsAtLessThan(
            UUID practitionerId, AppointmentStatus status, LocalDateTime from, LocalDateTime until);

    List<AppointmentRow> findByPatientIdAndStatusNotAndStartsAtGreaterThanEqualAndStartsAtLessThan(
            UUID patientId, AppointmentStatus status, LocalDateTime from, LocalDateTime until);

    List<AppointmentRow> findByStatusNotAndStartsAtGreaterThanEqualAndStartsAtLessThan(
            AppointmentStatus status, LocalDateTime from, LocalDateTime until);

    List<AppointmentRow> findByPatientId(UUID patientId);

    List<AppointmentRow> findByStatusAndStartsAtLessThanEqual(
            AppointmentStatus status, LocalDateTime instant);
}
