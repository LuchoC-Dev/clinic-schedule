package com.clinic.schedule.portal.application.port;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.clinic.schedule.portal.domain.RecordCopyRequest;

/**
 * The copies of clinical records, kept and put together by the clinical record module (D-95). The portal says who
 * may ask for whose; this port asks, follows them and downloads them.
 */
public interface RecordCopyPort {

    /**
     * @param requestedByAccount the name the asking account goes by (D-81)
     * @throws com.clinic.schedule.portal.application.exception.RecordCopyWaitingException when the record already has
     *         a copy waiting
     */
    RecordCopyRequest request(UUID patientId, UUID requestedBy, String requestedByAccount,
            RecordCopyRequest.Delivery delivery);

    /** Every request of these patients' records, the most recent first. */
    List<RecordCopyRequest> findForPatients(Collection<UUID> patientIds);

    /**
     * The PDF, recorded as a reading of the record by {@code readBy}.
     *
     * @return empty when there is no such copy among those patients'
     * @throws com.clinic.schedule.portal.application.exception.RecordCopyNotReadyException when it goes on paper,
     *         or is not authenticated yet
     */
    Optional<byte[]> downloadPdf(UUID copyId, Collection<UUID> patientIds, String readBy);
}
