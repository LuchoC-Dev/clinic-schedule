package com.clinic.schedule.appointment.adapter.out.external;

import org.springframework.stereotype.Component;

import com.clinic.schedule.appointment.api.AppointmentArrivals;
import com.clinic.schedule.appointment.application.port.ArrivalPort;
import com.clinic.schedule.appointment.domain.AppointmentId;

/**
 * Answers {@link ArrivalPort} through this module's own {@link AppointmentArrivals}, which the
 * arrival module implements (D-64). The use cases see a port like any other; only this class knows
 * the answer comes from outside, and it names no other module.
 */
@Component
class ArrivalAdapter implements ArrivalPort {

    private final AppointmentArrivals arrivals;

    ArrivalAdapter(AppointmentArrivals arrivals) {
        this.arrivals = arrivals;
    }

    @Override
    public void appointmentCancelled(AppointmentId id) {
        arrivals.appointmentCancelled(id.value());
    }
}
