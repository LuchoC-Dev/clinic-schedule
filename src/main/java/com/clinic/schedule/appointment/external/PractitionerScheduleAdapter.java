package com.clinic.schedule.appointment.external;

import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.clinic.schedule.appointment.application.PractitionerSchedulePort;
import com.clinic.schedule.appointment.domain.PractitionerId;
import com.clinic.schedule.appointment.domain.TimeSlot;
import com.clinic.schedule.practitioner.api.PractitionerReader;
import com.clinic.schedule.practitioner.api.TimeOffWriter;

/**
 * Answers this module's {@link PractitionerSchedulePort} by asking the practitioner module
 * through its api. With {@link PatientRegistryAdapter}, the only classes here importing from
 * another module, and only from its {@code api/} (D-05).
 *
 * <p>{@code external/} holds the adapters towards other modules, as {@code persistence/}
 * holds the one towards the database.
 */
@Component
class PractitionerScheduleAdapter implements PractitionerSchedulePort {

    private final PractitionerReader practitioners;
    private final TimeOffWriter timeOff;

    PractitionerScheduleAdapter(PractitionerReader practitioners, TimeOffWriter timeOff) {
        this.practitioners = practitioners;
        this.timeOff = timeOff;
    }

    @Override
    public Optional<BookableReason> findReason(PractitionerId practitionerId, UUID reasonId) {
        return practitioners.findReason(practitionerId.value(), reasonId)
                .map(reason -> new BookableReason(reason.name(), reason.durationMinutes()));
    }

    @Override
    public boolean worksDuring(PractitionerId practitionerId, TimeSlot slot) {
        return practitioners.worksDuring(practitionerId.value(), slot.startsAt(), slot.endsAt());
    }

    @Override
    public boolean recordTimeOff(PractitionerId practitionerId, TimeSlot span) {
        return timeOff.recordTimeOff(practitionerId.value(), span.startsAt(), span.endsAt());
    }
}
