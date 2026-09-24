package com.clinic.schedule.appointment.application.usecase;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import com.clinic.schedule.appointment.application.port.PractitionerSchedulePort;
import com.clinic.schedule.appointment.domain.PractitionerId;
import com.clinic.schedule.appointment.domain.TimeSlot;

/**
 * The practitioner port answered from memory, so the use cases can be tested without the
 * practitioner module.
 *
 * <p>A practitioner works at any time unless the test narrows it with {@link #worksOnly}:
 * most tests are about something else, and should not have to set up a week to book.
 */
class FakePractitionerSchedule implements PractitionerSchedulePort {

    private record Window(LocalDateTime from, LocalDateTime until) {
    }

    private final Map<PractitionerId, Map<UUID, BookableReason>> reasons = new HashMap<>();
    private final Map<PractitionerId, List<Window>> windows = new HashMap<>();

    /** Gives the practitioner a reason and returns its id. */
    UUID givenReason(PractitionerId practitionerId, String name, int durationMinutes) {
        UUID id = UUID.randomUUID();
        reasons.computeIfAbsent(practitionerId, key -> new HashMap<>())
                .put(id, new BookableReason(name, durationMinutes));
        return id;
    }

    /** From now on the practitioner works only inside the given windows. */
    void worksOnly(PractitionerId practitionerId, LocalDateTime from, LocalDateTime until) {
        windows.computeIfAbsent(practitionerId, key -> new ArrayList<>()).add(new Window(from, until));
    }

    @Override
    public Optional<BookableReason> findReason(PractitionerId practitionerId, UUID reasonId) {
        return Optional.ofNullable(reasons.getOrDefault(practitionerId, Map.of()).get(reasonId));
    }

    private final java.util.Set<PractitionerId> unknown = new java.util.HashSet<>();
    private final List<TimeSlot> recordedTimeOff = new ArrayList<>();

    /** From now on the practitioner does not exist, so recording their time off fails. */
    void givenUnknown(PractitionerId practitionerId) {
        unknown.add(practitionerId);
    }

    List<TimeSlot> recordedTimeOff() {
        return recordedTimeOff;
    }

    @Override
    public boolean recordTimeOff(PractitionerId practitionerId, TimeSlot span) {
        if (unknown.contains(practitionerId)) {
            return false;
        }
        recordedTimeOff.add(span);
        return true;
    }

    @Override
    public boolean worksDuring(PractitionerId practitionerId, TimeSlot slot) {
        List<Window> allowed = windows.get(practitionerId);
        if (allowed == null) {
            return true;
        }
        return allowed.stream().anyMatch(window ->
                !slot.startsAt().isBefore(window.from()) && !slot.endsAt().isAfter(window.until()));
    }
}
