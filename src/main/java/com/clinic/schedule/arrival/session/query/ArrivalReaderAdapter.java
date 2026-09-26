package com.clinic.schedule.arrival.session.query;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.clinic.schedule.arrival.api.ArrivalReader;
import com.clinic.schedule.arrival.api.QueueArrival;

/**
 * Answers this module's {@link ArrivalReader} for the others, from the sessions' projection (D-63).
 * The clinical record asks it whether a practitioner is seeing a patient today.
 */
@Component
class ArrivalReaderAdapter implements ArrivalReader {

    private final SessionViewReader sessions;

    ArrivalReaderAdapter(SessionViewReader sessions) {
        this.sessions = sessions;
    }

    @Override
    @Transactional(readOnly = true)
    public List<QueueArrival> findForPatientOn(UUID patientId, LocalDate day) {
        return sessions.arrivalsOf(patientId, day);
    }
}
