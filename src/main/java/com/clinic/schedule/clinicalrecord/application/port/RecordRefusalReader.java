package com.clinic.schedule.clinicalrecord.application.port;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import com.clinic.schedule.clinicalrecord.domain.PatientId;
import com.clinic.schedule.clinicalrecord.domain.RecordRefusal;

/** The refused attempts to reach clinical content, as the admins look through them (D-72). Read only. */
public interface RecordRefusalReader {

    /**
     * The refusals from {@code from}, included, to {@code to}, excluded, the most recent first, at most
     * {@code limit} of them.
     *
     * @param patients only those about these patients' records; null for any, and those of no known
     *        patient too
     * @param refusedBy only this user's; null for anyone's
     */
    List<RecordRefusal> find(Set<PatientId> patients, String refusedBy, LocalDateTime from, LocalDateTime to,
            int limit);
}
