package com.clinic.schedule.clinicalrecord.api;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * The copies of their clinical records the patients asked for (D-95), for the patients' web: where each stands,
 * and the PDF once it is authenticated. Which patients the caller may ask about is the caller's to decide.
 */
public interface RecordCopyReader {

    /** Every request of these patients' records, the most recent first. */
    List<RecordCopySummary> findForPatients(Collection<UUID> patientIds);

    /**
     * The copy's PDF, recorded as a reading of the record by {@code readBy}.
     *
     * @param patientIds the patients whose copies the caller may have
     * @param readBy the name the account that downloads it goes by
     * @return empty when there is no such copy among those patients'
     * @throws RecordCopyNotReadyException when it goes on paper, or is not authenticated yet
     */
    Optional<byte[]> downloadPdf(UUID copyId, Collection<UUID> patientIds, String readBy);
}
