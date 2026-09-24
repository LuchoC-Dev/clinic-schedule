package com.clinic.schedule.consultation.domain;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Period;
import java.util.Optional;

/**
 * Whether a practitioner may read a patient's clinical record, and whether they may record a
 * consultation of them (D-49).
 *
 * <ul>
 *   <li><b>Reading</b>: they recorded a consultation of the patient within the access period —
 *       each one starts it again — or they are seeing the patient today, even before the
 *       patient arrives. A future appointment grants nothing before its day, and a patient last
 *       seen longer ago than the period is a new one.</li>
 *   <li><b>Recording</b>: the patient has arrived to be seen by them today.</li>
 * </ul>
 *
 * <p>The period is given, not fixed here: it is configuration (D-49).
 */
public class ClinicalRecordAccessPolicy {

    private final Period accessPeriod;

    public ClinicalRecordAccessPolicy(Period accessPeriod) {
        if (accessPeriod == null || accessPeriod.isNegative() || accessPeriod.isZero()) {
            throw new IllegalArgumentException("The access period to a clinical record must be longer than nothing");
        }
        this.accessPeriod = accessPeriod;
    }

    /**
     * @param lastOwnConsultation when this practitioner last recorded a consultation of the
     *        patient, if ever
     * @param today the practice's today
     */
    public boolean mayRead(Optional<LocalDateTime> lastOwnConsultation, VisitToday visit, LocalDate today) {
        if (visit != VisitToday.NONE) {
            return true;
        }
        return lastOwnConsultation
                .map(last -> !today.isAfter(last.toLocalDate().plus(accessPeriod)))
                .orElse(false);
    }

    public boolean mayRecord(VisitToday visit) {
        return visit == VisitToday.ARRIVED;
    }
}
