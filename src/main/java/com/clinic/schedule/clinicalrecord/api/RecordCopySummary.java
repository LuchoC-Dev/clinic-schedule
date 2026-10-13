package com.clinic.schedule.clinicalrecord.api;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * A request for a copy of a record, as the patients' web shows it (D-95): whose, who asked, when, how it is
 * delivered, by when it is to be authenticated, and when it was authenticated and handed over. Nothing of the
 * record, and not who authenticated it: that is the practice's.
 *
 * @param authenticatedAt null while it waits
 * @param handedOverAt null unless reception handed the envelope over
 */
public record RecordCopySummary(UUID id, UUID patientId, UUID requestedBy, LocalDateTime requestedAt,
        CopyDeliveryChoice delivery, LocalDateTime deadline, LocalDateTime authenticatedAt,
        LocalDateTime handedOverAt) {
}
