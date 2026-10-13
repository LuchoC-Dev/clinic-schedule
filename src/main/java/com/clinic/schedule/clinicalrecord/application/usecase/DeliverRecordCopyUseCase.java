package com.clinic.schedule.clinicalrecord.application.usecase;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.clinic.schedule.clinicalrecord.application.exception.RecordCopyNotFoundException;
import com.clinic.schedule.clinicalrecord.application.port.CurrentPractitionerPort;
import com.clinic.schedule.clinicalrecord.application.port.RecordCopyRepository;
import com.clinic.schedule.clinicalrecord.application.port.RecordReadingWriter;
import com.clinic.schedule.clinicalrecord.domain.PatientId;
import com.clinic.schedule.clinicalrecord.domain.RecordCopy;
import com.clinic.schedule.clinicalrecord.domain.RecordCopyId;
import com.clinic.schedule.clinicalrecord.domain.RecordReading;

/**
 * A copy of a record reaches whoever asked for it, as they chose (D-95): a PDF downloaded from their account once
 * authenticated, or an envelope handed over by reception to whoever shows their document. Either is one more
 * reading of the record, by the patient's right to it (Ley 26.529, art. 14), recorded with the copy in the same
 * transaction (D-59): who read it is the account that downloaded it, or the user of reception who handed it
 * over. Reception never reads it: it hands over a sealed envelope (D-51).
 */
@Service
public class DeliverRecordCopyUseCase {

    private final RecordCopyRepository copies;
    private final RecordCopyRenderer renderer;
    private final RecordReadingWriter readings;
    private final CurrentPractitionerPort currentUser;
    private final Clock clock;

    public DeliverRecordCopyUseCase(RecordCopyRepository copies, RecordCopyRenderer renderer,
            RecordReadingWriter readings, CurrentPractitionerPort currentUser, Clock clock) {
        this.copies = copies;
        this.renderer = renderer;
        this.readings = readings;
        this.currentUser = currentUser;
        this.clock = clock;
    }

    /**
     * The PDF, for the account that asks, of one of the records it may ask for.
     *
     * @param patients the patients whose copies the caller may have: who may ask for whose is the portal's
     * @param readBy the name the account goes by (D-81)
     * @return empty when there is no such copy among those patients'
     * @throws com.clinic.schedule.clinicalrecord.domain.exception.RecordCopyRefusedException when it goes on paper
     *         or is not authenticated yet
     */
    @Transactional
    public Optional<byte[]> download(RecordCopyId id, Collection<PatientId> patients, String readBy) {
        Optional<RecordCopy> found = copies.findById(id).filter(copy -> patients.contains(copy.patientId()));
        if (found.isEmpty()) {
            return Optional.empty();
        }
        RecordCopy copy = found.get();
        copy.requireDownloadable();
        readings.save(RecordReading.ofDeliveredCopy(readBy, copy.patientId(), LocalDateTime.now(clock), copy.id()));
        return Optional.of(renderer.render(copy));
    }

    /**
     * Reception hands the envelope over, having seen the document of whoever collects it.
     *
     * @throws RecordCopyNotFoundException when there is no such copy
     * @throws com.clinic.schedule.clinicalrecord.domain.exception.RecordCopyRefusedException when it is not on
     *         paper, not authenticated, handed over already, or nobody's document was seen
     */
    @Transactional
    public RecordCopy handOver(RecordCopyId id, boolean documentSeen) {
        LocalDateTime now = LocalDateTime.now(clock);
        RecordCopy copy = copies.findById(id).orElseThrow(RecordCopyNotFoundException::new);
        RecordCopy handed = copy.handedOver(currentUser.username(), now, documentSeen);
        copies.save(handed);
        readings.save(RecordReading.ofDeliveredCopy(currentUser.username(), copy.patientId(), now, copy.id()));
        return handed;
    }
}
