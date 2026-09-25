package com.clinic.schedule.clinicalrecord.application.usecase;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.clinic.schedule.clinicalrecord.application.exception.ClinicalRecordAccessDeniedException;
import com.clinic.schedule.clinicalrecord.application.exception.UnknownOrderException;
import com.clinic.schedule.clinicalrecord.application.port.CheckOrderRepository;
import com.clinic.schedule.clinicalrecord.application.port.CheckRepository;
import com.clinic.schedule.clinicalrecord.application.port.ConsultationRepository;
import com.clinic.schedule.clinicalrecord.application.port.CurrentPractitionerPort;
import com.clinic.schedule.clinicalrecord.domain.Check;
import com.clinic.schedule.clinicalrecord.domain.CheckId;
import com.clinic.schedule.clinicalrecord.domain.CheckOrder;
import com.clinic.schedule.clinicalrecord.domain.CheckOrderId;
import com.clinic.schedule.clinicalrecord.domain.Consultation;
import com.clinic.schedule.clinicalrecord.domain.ConsultationId;
import com.clinic.schedule.clinicalrecord.domain.ConsultationItem;
import com.clinic.schedule.clinicalrecord.domain.PatientId;
import com.clinic.schedule.clinicalrecord.domain.PractitionerId;
import com.clinic.schedule.clinicalrecord.domain.VisitToday;

/**
 * A practitioner sends the consultation of a patient they are seeing today, with the checks done
 * during it and the orders issued in it. It is recorded whole, once, with the practice's clock; the
 * consultation never changes afterwards, each check only gains its result if it was pending, and a
 * check may fulfil one of the patient's open orders (D-47, D-53, D-55).
 *
 * <p>The consultation, its checks, its orders and the orders its checks fulfil are stored in one
 * transaction: all of them or none.
 *
 * <p>The practitioner is whoever is using the application, never something the caller names.
 * The patient must have arrived to be seen by them today (D-49); otherwise the refusal is a 403,
 * and it says whether the practitioner could at least read the record, so a screen can tell
 * "not arrived yet" from "not yours".
 *
 * <p>Retrying is safe: an id already recorded by this practitioner answers that consultation and
 * its checks instead of recording them twice (D-19). An id recorded by someone else is refused,
 * rather than answered with a consultation the user might not be allowed to read.
 */
@Service
public class RecordConsultationUseCase {

    private final ConsultationRepository consultations;
    private final CheckRepository checks;
    private final CheckOrderRepository orders;
    private final CurrentPractitionerPort currentPractitioner;
    private final ClinicalRecordAccessReader access;
    private final Clock clock;

    public RecordConsultationUseCase(ConsultationRepository consultations, CheckRepository checks,
            CheckOrderRepository orders, CurrentPractitionerPort currentPractitioner, ClinicalRecordAccessReader access,
            Clock clock) {
        this.consultations = consultations;
        this.checks = checks;
        this.orders = orders;
        this.currentPractitioner = currentPractitioner;
        this.access = access;
        this.clock = clock;
    }

    @Transactional
    public Recorded handle(Command command) {
        PractitionerId practitionerId = currentPractitioner.practitionerId()
                .orElseThrow(ClinicalRecordAccessDeniedException::toRead);

        Optional<Consultation> alreadyRecorded = consultations.findById(command.id());
        if (alreadyRecorded.isPresent()) {
            if (!alreadyRecorded.get().practitionerId().equals(practitionerId)) {
                throw ClinicalRecordAccessDeniedException.toRead();
            }
            return new Recorded(alreadyRecorded.get(), checks.findAllForConsultation(command.id()),
                    ordersOf(alreadyRecorded.get()));
        }

        LocalDateTime now = LocalDateTime.now(clock);
        VisitToday visit = access.visitOn(practitionerId, command.patientId(), now.toLocalDate());
        if (!access.mayRecord(visit)) {
            boolean mayRead = access.mayRead(practitionerId, command.patientId(), visit, now.toLocalDate());
            throw mayRead ? ClinicalRecordAccessDeniedException.toRecord() : ClinicalRecordAccessDeniedException.toRead();
        }

        Consultation consultation = Consultation.record(command.id(), command.patientId(), practitionerId, now,
                command.notes(), command.items());
        List<CheckOrder> issued = command.orders().stream()
                .map(description -> CheckOrder.issue(CheckOrderId.newId(), command.patientId(), practitionerId, now,
                        command.id(), description))
                .toList();
        consultations.save(consultation);
        issued.forEach(orders::save);

        List<Check> done = new ArrayList<>();
        for (CheckDone checkDone : command.checks()) {
            Check check = checkDone.toCheck(command.patientId(), practitionerId, now, command.id(),
                    descriptionOfOrder(checkDone.orderId(), command.patientId()));
            checks.save(check);
            if (checkDone.orderId() != null) {
                OrderFulfilment.fulfil(orders, checkDone.orderId(), check);
            }
            done.add(check);
        }
        return new Recorded(consultation, done, issued);
    }

    /** What the order asks for, to describe a check that fulfils it and was left without a description. */
    private String descriptionOfOrder(CheckOrderId orderId, PatientId patientId) {
        if (orderId == null) {
            return null;
        }
        return orders.findById(orderId)
                .filter(order -> order.patientId().equals(patientId))
                .map(CheckOrder::description)
                .orElseThrow(() -> new UnknownOrderException(orderId));
    }

    private List<CheckOrder> ordersOf(Consultation consultation) {
        return orders.findAllForPatient(consultation.patientId()).stream()
                .filter(order -> order.consultationId().equals(consultation.id()))
                .toList();
    }

    /**
     * What the practitioner writes. Who writes it and when are not asked: they are known.
     *
     * @param checks the checks done during the consultation, in the order they were written
     * @param orders what each order issued during the consultation asks for (D-55)
     */
    public record Command(ConsultationId id, PatientId patientId, String notes, List<ConsultationItem> items,
            List<CheckDone> checks, List<String> orders) {

        public Command {
            checks = checks == null ? List.of() : List.copyOf(checks);
            orders = orders == null ? List.of() : List.copyOf(orders);
            if (checks.size() > Consultation.MAX_ITEMS || orders.size() > Consultation.MAX_ITEMS) {
                throw new IllegalArgumentException(
                        "A consultation has at most " + Consultation.MAX_ITEMS + " checks and as many orders");
            }
        }
    }

    /**
     * A check done during the consultation: what was done, its result when it is known at once, and
     * the order it fulfils, if any. A check that fulfils an order may leave its description out: it
     * is what the order asks for.
     */
    public record CheckDone(String description, String result, CheckOrderId orderId) {

        Check toCheck(PatientId patientId, PractitionerId practitionerId, LocalDateTime at,
                ConsultationId consultationId, String orderDescription) {
            String described = (description == null || description.isBlank()) && orderDescription != null
                    ? orderDescription : description;
            return result == null || result.isBlank()
                    ? Check.perform(CheckId.newId(), patientId, practitionerId, at, described, consultationId)
                    : Check.performWithResult(CheckId.newId(), patientId, practitionerId, at, described,
                            consultationId, result);
        }
    }

    /** The consultation recorded, the checks done during it and the orders issued in it. */
    public record Recorded(Consultation consultation, List<Check> checks, List<CheckOrder> orders) {
    }
}
