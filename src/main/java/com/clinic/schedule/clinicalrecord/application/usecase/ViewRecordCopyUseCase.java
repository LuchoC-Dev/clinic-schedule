package com.clinic.schedule.clinicalrecord.application.usecase;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.clinic.schedule.clinicalrecord.application.exception.ClinicalRecordAccessDeniedException;
import com.clinic.schedule.clinicalrecord.application.exception.RecordCopyNotFoundException;
import com.clinic.schedule.clinicalrecord.application.port.CurrentPractitionerPort;
import com.clinic.schedule.clinicalrecord.application.port.RecordCopyRepository;
import com.clinic.schedule.clinicalrecord.application.port.RecordReadingWriter;
import com.clinic.schedule.clinicalrecord.application.port.RecordRefusalWriter;
import com.clinic.schedule.clinicalrecord.domain.CopyCode;
import com.clinic.schedule.clinicalrecord.domain.PatientId;
import com.clinic.schedule.clinicalrecord.domain.PractitionerId;
import com.clinic.schedule.clinicalrecord.domain.ReadAccess;
import com.clinic.schedule.clinicalrecord.domain.RecordCopy;
import com.clinic.schedule.clinicalrecord.domain.RecordCopyId;
import com.clinic.schedule.clinicalrecord.domain.RecordReading;
import com.clinic.schedule.clinicalrecord.domain.RecordRefusal;

/**
 * A practitioner looks at a copy of a record (D-95): the request — whose, who asked, when, how it is delivered,
 * how long is left, who authenticated it — found by its id or by the code printed on it, to check a copy against
 * the application; and the copy itself, the document, to review it before authenticating it or to print it.
 *
 * <p>The request is no reading: it shows who and when, never the record. The document is one, and only for whoever
 * may read that record (D-49): it is recorded as such, with the copy, in the same transaction, and a refusal is
 * recorded on its own (D-59, D-72).
 */
@Service
public class ViewRecordCopyUseCase {

    private final RecordCopyRepository copies;
    private final RecordCopyRenderer renderer;
    private final RecordReadingWriter readings;
    private final RecordRefusalWriter refusals;
    private final CurrentPractitionerPort currentPractitioner;
    private final ClinicalRecordAccessReader access;
    private final Clock clock;

    public ViewRecordCopyUseCase(RecordCopyRepository copies, RecordCopyRenderer renderer,
            RecordReadingWriter readings, RecordRefusalWriter refusals, CurrentPractitionerPort currentPractitioner,
            ClinicalRecordAccessReader access, Clock clock) {
        this.copies = copies;
        this.renderer = renderer;
        this.readings = readings;
        this.refusals = refusals;
        this.currentPractitioner = currentPractitioner;
        this.access = access;
        this.clock = clock;
    }

    /**
     * @throws RecordCopyNotFoundException when there is no such copy
     * @throws ClinicalRecordAccessDeniedException when the user is not a practitioner
     */
    @Transactional(readOnly = true)
    public View find(RecordCopyId id) {
        return view(copies.findById(id).orElseThrow(RecordCopyNotFoundException::new));
    }

    /**
     * The copy a code printed on paper belongs to.
     *
     * @throws RecordCopyNotFoundException when no copy has that code, or it cannot be one
     * @throws ClinicalRecordAccessDeniedException when the user is not a practitioner
     */
    @Transactional(readOnly = true)
    public View findByCode(String typed) {
        return view(CopyCode.read(typed).flatMap(copies::findByCode).orElseThrow(RecordCopyNotFoundException::new));
    }

    private View view(RecordCopy copy) {
        PractitionerId practitionerId = currentPractitioner.practitionerId()
                .orElseThrow(ClinicalRecordAccessDeniedException::toRead);
        return new View(copy, access.mayRead(practitionerId, copy.patientId(), LocalDate.now(clock)));
    }

    /**
     * The copy's document, as the record stood when it was put together.
     *
     * @throws RecordCopyNotFoundException when there is no such copy
     * @throws ClinicalRecordAccessDeniedException when the user may not read that record
     */
    @Transactional
    public Document document(RecordCopyId id) {
        LocalDateTime now = LocalDateTime.now(clock);
        RecordCopy copy = copies.findById(id).orElseThrow(RecordCopyNotFoundException::new);
        PractitionerId practitionerId = currentPractitioner.practitionerId()
                .orElseThrow(() -> refused(null, copy.patientId(), now));
        ReadAccess readAccess = access.readAccess(practitionerId, copy.patientId(), now.toLocalDate())
                .orElseThrow(() -> refused(practitionerId, copy.patientId(), now));
        readings.save(RecordReading.ofCopy(practitionerId, currentPractitioner.username(), copy.patientId(), now,
                readAccess, copy.id()));
        return new Document(copy, renderer.render(copy));
    }

    /** Records the refusal, and gives the 403 to throw. */
    private ClinicalRecordAccessDeniedException refused(PractitionerId practitionerId, PatientId patientId,
            LocalDateTime at) {
        refusals.save(RecordRefusal.ofRecord(currentPractitioner.username(), practitionerId, patientId, at));
        return ClinicalRecordAccessDeniedException.toRead();
    }

    /** A copy, and whether the practitioner may read its record: review it, authenticate it. */
    public record View(RecordCopy copy, boolean mayRead) {
    }

    /** A copy, and its document. */
    public record Document(RecordCopy copy, byte[] content) {
    }
}
