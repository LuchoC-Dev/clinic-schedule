package com.clinic.schedule.clinicalrecord.application.port;

import com.clinic.schedule.clinicalrecord.domain.RecordRefusal;

/**
 * Where the refused attempts to reach clinical content are kept (D-72). Only ever added to.
 *
 * <p>A refusal is recorded as it is refused, and refusing undoes whatever the use case had done: so
 * saving one must not depend on the use case's transaction, and keeps even when that one is rolled
 * back.
 */
public interface RecordRefusalWriter {

    void save(RecordRefusal refusal);
}
