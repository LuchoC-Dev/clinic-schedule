package com.clinic.schedule.appointment.adapter.out.external;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Map;
import java.util.stream.Collectors;

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

    @Override
    public Map<AppointmentId, LocalDateTime> arrivalsOf(Collection<AppointmentId> ids) {
        if (ids.isEmpty()) {
            return Map.of();
        }
        return arrivals.arrivalsOf(ids.stream().map(AppointmentId::value).toList()).entrySet().stream()
                .collect(Collectors.toMap(entry -> new AppointmentId(entry.getKey()), Map.Entry::getValue));
    }
}
