package com.clinic.schedule.clinicalrecord.application.port;

import com.clinic.schedule.clinicalrecord.domain.RecordReading;

/**
 * Where the readings of clinical records are kept (D-59). Only written: nobody reads them yet —
 * not on a screen, not through the API — and admins will, when they exist.
 */
public interface RecordReadingWriter {

    void save(RecordReading reading);
}
