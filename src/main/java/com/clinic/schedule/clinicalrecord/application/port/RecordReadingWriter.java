package com.clinic.schedule.clinicalrecord.application.port;

import com.clinic.schedule.clinicalrecord.domain.RecordReading;

/**
 * Where the readings of clinical records are kept (D-59). Only ever added to; the admins read them
 * through {@link RecordReadingReader} (D-71).
 */
public interface RecordReadingWriter {

    void save(RecordReading reading);
}
