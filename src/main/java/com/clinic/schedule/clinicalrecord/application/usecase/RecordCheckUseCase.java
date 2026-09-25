package com.clinic.schedule.clinicalrecord.application.usecase;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.clinic.schedule.clinicalrecord.application.exception.ClinicalRecordAccessDeniedException;
import com.clinic.schedule.clinicalrecord.application.exception.UnknownOrderException;
import com.clinic.schedule.clinicalrecord.application.port.CheckOrderRepository;
import com.clinic.schedule.clinicalrecord.application.port.CheckRepository;
import com.clinic.schedule.clinicalrecord.application.port.CurrentPractitionerPort;
import com.clinic.schedule.clinicalrecord.domain.Check;
import com.clinic.schedule.clinicalrecord.domain.CheckId;
import com.clinic.schedule.clinicalrecord.domain.CheckOrder;
import com.clinic.schedule.clinicalrecord.domain.CheckOrderId;
import com.clinic.schedule.clinicalrecord.domain.PatientId;
import com.clinic.schedule.clinicalrecord.domain.PractitionerId;
import com.clinic.schedule.clinicalrecord.domain.VisitToday;

/**
 * A check done without a consultation: the precaution for an emergency, when the patient is
 * checked before anyone writes a consultation (D-53). Checks done during a consultation are sent
 * with it, by {@link RecordConsultationUseCase}.
 *
 * <p>The patient is there to be checked, so the rule is the consultation's: they arrived to be
 * seen by this practitioner today (D-49). The result may come with it or later, and it may fulfil
 * one of the patient's open orders (D-55).
 *
 * <p>Retrying is safe: an id already recorded by this practitioner answers that check (D-19).
 */
@Service
public class RecordCheckUseCase {

    private final CheckRepository checks;
    private final CheckOrderRepository orders;
    private final CurrentPractitionerPort currentPractitioner;
    private final ClinicalRecordAccessReader access;
    private final Clock clock;

    public RecordCheckUseCase(CheckRepository checks, CheckOrderRepository orders,
            CurrentPractitionerPort currentPractitioner, ClinicalRecordAccessReader access, Clock clock) {
        this.checks = checks;
        this.orders = orders;
        this.currentPractitioner = currentPractitioner;
        this.access = access;
        this.clock = clock;
    }

    @Transactional
    public Check handle(Command command) {
        PractitionerId practitionerId = currentPractitioner.practitionerId()
                .orElseThrow(ClinicalRecordAccessDeniedException::toRead);

        Optional<Check> alreadyRecorded = checks.findById(command.id());
        if (alreadyRecorded.isPresent()) {
            if (!alreadyRecorded.get().performedBy().equals(practitionerId)) {
                throw ClinicalRecordAccessDeniedException.toRead();
            }
            return alreadyRecorded.get();
        }

        LocalDateTime now = LocalDateTime.now(clock);
        VisitToday visit = access.visitOn(practitionerId, command.patientId(), now.toLocalDate());
        if (!access.mayRecord(visit)) {
            boolean mayRead = access.mayRead(practitionerId, command.patientId(), visit, now.toLocalDate());
            throw mayRead ? ClinicalRecordAccessDeniedException.toRecord() : ClinicalRecordAccessDeniedException.toRead();
        }

        String description = command.description();
        if ((description == null || description.isBlank()) && command.orderId() != null) {
            description = orders.findById(command.orderId())
                    .filter(order -> order.patientId().equals(command.patientId()))
                    .map(CheckOrder::description)
                    .orElseThrow(() -> new UnknownOrderException(command.orderId()));
        }
        Check check = command.result() == null || command.result().isBlank()
                ? Check.perform(command.id(), command.patientId(), practitionerId, now, description, null)
                : Check.performWithResult(command.id(), command.patientId(), practitionerId, now, description, null,
                        command.result());
        checks.save(check);
        if (command.orderId() != null) {
            OrderFulfilment.fulfil(orders, command.orderId(), check);
        }
        return check;
    }

    /**
     * @param result null or blank while it is pending
     * @param orderId the order it fulfils, if any; without a description, the check is what the order asks for
     */
    public record Command(CheckId id, PatientId patientId, String description, String result, CheckOrderId orderId) {
    }
}
