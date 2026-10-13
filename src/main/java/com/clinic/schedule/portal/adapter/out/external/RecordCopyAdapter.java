package com.clinic.schedule.portal.adapter.out.external;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.clinic.schedule.clinicalrecord.api.CopyDeliveryChoice;
import com.clinic.schedule.clinicalrecord.api.RecordCopyAlreadyRequestedException;
import com.clinic.schedule.clinicalrecord.api.RecordCopyNotReadyException;
import com.clinic.schedule.clinicalrecord.api.RecordCopyReader;
import com.clinic.schedule.clinicalrecord.api.RecordCopySummary;
import com.clinic.schedule.clinicalrecord.api.RecordCopyWriter;
import com.clinic.schedule.portal.application.exception.RecordCopyWaitingException;
import com.clinic.schedule.portal.application.port.RecordCopyPort;
import com.clinic.schedule.portal.domain.RecordCopyRequest;

/** Answers {@link RecordCopyPort} through the clinical record module's api (D-05, D-95). */
@Component
class RecordCopyAdapter implements RecordCopyPort {

    private final RecordCopyWriter writer;
    private final RecordCopyReader reader;

    RecordCopyAdapter(RecordCopyWriter writer, RecordCopyReader reader) {
        this.writer = writer;
        this.reader = reader;
    }

    @Override
    public RecordCopyRequest request(UUID patientId, UUID requestedBy, String requestedByAccount,
            RecordCopyRequest.Delivery delivery) {
        try {
            return toRequest(writer.request(patientId, requestedBy, requestedByAccount,
                    CopyDeliveryChoice.valueOf(delivery.name())));
        } catch (RecordCopyAlreadyRequestedException e) {
            throw new RecordCopyWaitingException();
        }
    }

    @Override
    public List<RecordCopyRequest> findForPatients(Collection<UUID> patientIds) {
        return reader.findForPatients(patientIds).stream().map(RecordCopyAdapter::toRequest).toList();
    }

    @Override
    public Optional<byte[]> downloadPdf(UUID copyId, Collection<UUID> patientIds, String readBy) {
        try {
            return reader.downloadPdf(copyId, patientIds, readBy);
        } catch (RecordCopyNotReadyException e) {
            throw new com.clinic.schedule.portal.application.exception.RecordCopyNotReadyException();
        }
    }

    private static RecordCopyRequest toRequest(RecordCopySummary copy) {
        return new RecordCopyRequest(copy.id(), copy.patientId(), copy.requestedBy(), copy.requestedAt(),
                RecordCopyRequest.Delivery.valueOf(copy.delivery().name()), copy.deadline(), copy.authenticatedAt(),
                copy.handedOverAt());
    }
}
