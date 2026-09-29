package com.clinic.schedule.clinicalrecord.application.port;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import com.clinic.schedule.clinicalrecord.domain.PractitionerId;
import com.clinic.schedule.clinicalrecord.domain.PatientId;
import com.clinic.schedule.clinicalrecord.domain.RecordReading;

/** The readings of clinical records, as the admins look through them (D-71). Read only. */
public interface RecordReadingReader {

    /**
     * The readings made from {@code from}, included, to {@code to}, excluded, the most recent first, at
     * most {@code limit} of them.
     *
     * @param patients only these patients' records; null for anyone's
     * @param practitioner only this practitioner's readings; null for anyone's
     */
    List<RecordReading> find(Set<PatientId> patients, PractitionerId practitioner, LocalDateTime from,
            LocalDateTime to, int limit);
}
