package com.clinic.schedule.clinicalrecord.application.usecase;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.clinic.schedule.clinicalrecord.application.exception.ClinicalRecordAccessDeniedException;
import com.clinic.schedule.clinicalrecord.application.port.CheckOrderRepository;
import com.clinic.schedule.clinicalrecord.application.port.CheckRepository;
import com.clinic.schedule.clinicalrecord.application.port.CurrentPractitionerPort;
import com.clinic.schedule.clinicalrecord.domain.Check;
import com.clinic.schedule.clinicalrecord.domain.CheckOrder;
import com.clinic.schedule.clinicalrecord.domain.OrderOutcome;
import com.clinic.schedule.clinicalrecord.domain.PractitionerId;

/**
 * The orders the user issued that were not cancelled, each with where it stands — open, done and
 * waiting for its result, or reported — and whether the user may still read the patient's record
 * (D-56). Only the user's own: whoever issued an order is who follows it up.
 *
 * <p>It answers where each order stands, not what its result says: that is in the record, behind
 * the access rule, as in the list of pending results (D-54).
 */
@Service
public class ListMyOrdersUseCase {

    private final CheckOrderRepository orders;
    private final CheckRepository checks;
    private final CurrentPractitionerPort currentPractitioner;
    private final ClinicalRecordAccessReader access;
    private final Clock clock;

    public ListMyOrdersUseCase(CheckOrderRepository orders, CheckRepository checks,
            CurrentPractitionerPort currentPractitioner, ClinicalRecordAccessReader access, Clock clock) {
        this.orders = orders;
        this.checks = checks;
        this.currentPractitioner = currentPractitioner;
        this.access = access;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<MyOrder> handle() {
        PractitionerId practitionerId = currentPractitioner.practitionerId()
                .orElseThrow(ClinicalRecordAccessDeniedException::toRead);
        LocalDate today = LocalDate.now(clock);
        return orders.findAllIssuedBy(practitionerId).stream()
                .filter(order -> !(order.outcome().orElse(null) instanceof OrderOutcome.Cancelled))
                .map(order -> new MyOrder(order, checkOf(order),
                        access.mayRead(practitionerId, order.patientId(), today)))
                .toList();
    }

    private Optional<Check> checkOf(CheckOrder order) {
        return order.outcome()
                .filter(OrderOutcome.Fulfilled.class::isInstance)
                .map(OrderOutcome.Fulfilled.class::cast)
                .flatMap(fulfilled -> checks.findById(fulfilled.check()));
    }

    /**
     * An order the user issued, the check that fulfilled it if any, and whether the user may still
     * read its patient's record.
     */
    public record MyOrder(CheckOrder order, Optional<Check> check, boolean mayRead) {

        public Situation situation() {
            return check.map(done -> done.isPending() ? Situation.AWAITING_RESULT : Situation.REPORTED)
                    .orElse(Situation.OPEN);
        }
    }

    /** Where an order the user follows up stands. */
    public enum Situation {

        /** Nobody did the check yet. */
        OPEN,

        /** The check was done, and its result has not arrived. */
        AWAITING_RESULT,

        /** The check has its result. */
        REPORTED
    }
}
