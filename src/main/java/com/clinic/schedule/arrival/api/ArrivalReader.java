package com.clinic.schedule.arrival.api;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * What other modules may ask about the queues (D-63). Read-only, and it answers facts, not what
 * they mean: whether being in a queue lets a practitioner read or write a record is the clinical
 * record's rule, not this one's.
 */
public interface ArrivalReader {

    /**
     * Where the patient stands in the queues on that day: every queue they wait in now, and every
     * one where a practitioner called or saw them that day.
     */
    List<QueueArrival> findForPatientOn(UUID patientId, LocalDate day);
}
