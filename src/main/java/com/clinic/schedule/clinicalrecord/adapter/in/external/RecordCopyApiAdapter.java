package com.clinic.schedule.clinicalrecord.adapter.in.external;

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
import com.clinic.schedule.clinicalrecord.application.usecase.DeliverRecordCopyUseCase;
import com.clinic.schedule.clinicalrecord.application.usecase.ListRecordCopiesUseCase;
import com.clinic.schedule.clinicalrecord.application.usecase.RequestRecordCopyUseCase;
import com.clinic.schedule.clinicalrecord.domain.CopyDelivery;
import com.clinic.schedule.clinicalrecord.domain.PatientId;
import com.clinic.schedule.clinicalrecord.domain.RecordCopy;
import com.clinic.schedule.clinicalrecord.domain.RecordCopyId;
import com.clinic.schedule.clinicalrecord.domain.exception.RecordCopyRefusedException;

/**
 * Answers the module's api for the copies of records (D-95): a driving adapter called by another module rather
 * than by HTTP, which calls the use cases like any other (D-48), and tells their refusals in the api's terms.
 */
@Component
class RecordCopyApiAdapter implements RecordCopyWriter, RecordCopyReader {

    private final RequestRecordCopyUseCase request;
    private final ListRecordCopiesUseCase list;
    private final DeliverRecordCopyUseCase deliver;

    RecordCopyApiAdapter(RequestRecordCopyUseCase request, ListRecordCopiesUseCase list,
            DeliverRecordCopyUseCase deliver) {
        this.request = request;
        this.list = list;
        this.deliver = deliver;
    }

    @Override
    public RecordCopySummary request(UUID patientId, UUID requestedBy, String requestedByAccount,
            CopyDeliveryChoice delivery) {
        try {
            return summary(request.handle(new RequestRecordCopyUseCase.Command(new PatientId(patientId), requestedBy,
                    requestedByAccount, CopyDelivery.valueOf(delivery.name()))));
        } catch (RecordCopyRefusedException e) {
            throw new RecordCopyAlreadyRequestedException();
        }
    }

    @Override
    public List<RecordCopySummary> findForPatients(Collection<UUID> patientIds) {
        return list.forPatients(patientIds.stream().map(PatientId::new).toList()).stream()
                .map(RecordCopyApiAdapter::summary)
                .toList();
    }

    @Override
    public Optional<byte[]> downloadPdf(UUID copyId, Collection<UUID> patientIds, String readBy) {
        try {
            return deliver.download(new RecordCopyId(copyId), patientIds.stream().map(PatientId::new).toList(), readBy);
        } catch (RecordCopyRefusedException e) {
            throw new RecordCopyNotReadyException();
        }
    }

    private static RecordCopySummary summary(RecordCopy copy) {
        return new RecordCopySummary(copy.id().value(), copy.patientId().value(), copy.requestedBy(),
                copy.requestedAt(), CopyDeliveryChoice.valueOf(copy.delivery().name()), copy.deadline(),
                copy.authentication().map(RecordCopy.Authentication::at).orElse(null),
                copy.handover().map(RecordCopy.Handover::at).orElse(null));
    }
}
