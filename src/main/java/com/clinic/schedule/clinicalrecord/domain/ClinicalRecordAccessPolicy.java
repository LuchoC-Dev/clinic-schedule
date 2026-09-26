package com.clinic.schedule.clinicalrecord.domain;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Period;
import java.util.List;
import java.util.Optional;

/**
 * Whether a practitioner may read a patient's clinical record, and whether they may record a
 * consultation or a check of them (D-49, D-53, D-54).
 *
 * <ul>
 *   <li><b>Reading</b>, in any of three ways:
 *     <ul>
 *       <li>they saw the patient — recorded a consultation of them, or did a check to them —
 *           within the access period; each time starts it again, and a patient last seen longer
 *           ago is a new one;</li>
 *       <li>they are seeing the patient today, even before the patient arrives; a future
 *           appointment grants nothing before its day;</li>
 *       <li>they were granted access, to report a check, and the grant is still valid.</li>
 *     </ul></li>
 *   <li><b>Recording</b>: the patient has arrived to be seen by them today. A grant does not
 *       allow it: it opens the record, it does not bring the patient in.</li>
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
     * By which access the practitioner may read the record, or empty when they may not. When more
     * than one applies, the first in the order above: that is the one a reading records (D-59).
     *
     * @param lastSeen when this practitioner last saw the patient, if ever
     * @param grants the access grants this practitioner holds for the patient, valid or not
     * @param today the practice's today
     */
    public Optional<ReadAccess> readAccess(Optional<LocalDateTime> lastSeen, VisitToday visit,
            List<AccessGrant> grants, LocalDate today) {
        if (visit != VisitToday.NONE) {
            return Optional.of(ReadAccess.VISIT_TODAY);
        }
        if (grants.stream().anyMatch(grant -> grant.isValidOn(today))) {
            return Optional.of(ReadAccess.ACCESS_GRANT);
        }
        return lastSeen
                .filter(last -> !today.isAfter(last.toLocalDate().plus(accessPeriod)))
                .map(last -> ReadAccess.SEEN_WITHIN_PERIOD);
    }

    /** Whether the practitioner may read the record at all, by any access. */
    public boolean mayRead(Optional<LocalDateTime> lastSeen, VisitToday visit, List<AccessGrant> grants,
            LocalDate today) {
        return readAccess(lastSeen, visit, grants, today).isPresent();
    }

    public boolean mayRecord(VisitToday visit) {
        return visit == VisitToday.ARRIVED;
    }
}
