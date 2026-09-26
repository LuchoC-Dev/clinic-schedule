package com.clinic.schedule.appointment.application.usecase;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.clinic.schedule.appointment.application.port.ArrivalPort;
import com.clinic.schedule.appointment.domain.AppointmentId;

/**
 * The arrivals, as the use cases see them (D-64, D-65): which appointments' patients arrived, and
 * which appointments the use cases said were cancelled.
 */
class FakeArrivals implements ArrivalPort {

    private final Map<AppointmentId, LocalDateTime> arrived = new HashMap<>();
    private final List<AppointmentId> cancelled = new ArrayList<>();

    /** The patient of that appointment arrived at that time. */
    FakeArrivals arrived(AppointmentId id, LocalDateTime at) {
        arrived.put(id, at);
        return this;
    }

    @Override
    public void appointmentCancelled(AppointmentId id) {
        cancelled.add(id);
    }

    @Override
    public Map<AppointmentId, LocalDateTime> arrivalsOf(Collection<AppointmentId> ids) {
        Map<AppointmentId, LocalDateTime> found = new HashMap<>();
        ids.stream().filter(arrived::containsKey).forEach(id -> found.put(id, arrived.get(id)));
        return found;
    }

    List<AppointmentId> cancelled() {
        return cancelled;
    }
}
