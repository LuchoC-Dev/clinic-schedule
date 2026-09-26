package com.clinic.schedule.appointment.application.usecase;

import java.util.ArrayList;
import java.util.List;

import com.clinic.schedule.appointment.application.port.ArrivalPort;
import com.clinic.schedule.appointment.domain.AppointmentId;

/** The arrivals, as far as the use cases tell them anything: which appointments were cancelled (D-64). */
class FakeArrivals implements ArrivalPort {

    private final List<AppointmentId> cancelled = new ArrayList<>();

    @Override
    public void appointmentCancelled(AppointmentId id) {
        cancelled.add(id);
    }

    List<AppointmentId> cancelled() {
        return cancelled;
    }
}
