package com.clinic.schedule.appointment.application.usecase;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.clinic.schedule.appointment.application.port.ArrivalPort;
import com.clinic.schedule.appointment.domain.AppointmentId;

/**
 * When the patients of those appointments arrived, for the agendas and the API to show (D-65). The
 * arrivals are the queues', and this is how the screens of this module see them.
 */
@Service
public class FindArrivalsUseCase {

    private final ArrivalPort arrivals;

    public FindArrivalsUseCase(ArrivalPort arrivals) {
        this.arrivals = arrivals;
    }

    @Transactional(readOnly = true)
    public Map<AppointmentId, LocalDateTime> handle(Collection<AppointmentId> ids) {
        return arrivals.arrivalsOf(ids);
    }
}
