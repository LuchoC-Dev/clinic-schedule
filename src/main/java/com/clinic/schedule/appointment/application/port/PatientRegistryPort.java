package com.clinic.schedule.appointment.application.port;

import com.clinic.schedule.appointment.domain.PatientId;

/**
 * Whether a patient is registered. Booking asks before reserving time for someone, so an
 * appointment can never point at a patient who does not exist.
 *
 * <p>A {@code Port}: it is another module, not storage (D-18). Declared in this module's
 * terms and answered by an adapter, like {@link PractitionerSchedulePort} (D-29).
 */
public interface PatientRegistryPort {

    boolean exists(PatientId patientId);
}
