package com.clinic.schedule.cashdesk.adapter.in.external;

import java.math.BigDecimal;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.clinic.schedule.cashdesk.api.ChargeRefusedException;
import com.clinic.schedule.cashdesk.api.Means;
import com.clinic.schedule.cashdesk.api.VisitCashier;
import com.clinic.schedule.cashdesk.application.usecase.StartVisitUseCase;
import com.clinic.schedule.cashdesk.domain.exception.ChargeRuleViolationException;

/**
 * Answers this module's {@link VisitCashier} for the queues (D-87): it starts the visit through a use case,
 * in the caller's transaction, and turns the api's means into this module's (D-89), and this module's refusals
 * into the api's own.
 */
@Component
class VisitCashierAdapter implements VisitCashier {

    private final StartVisitUseCase startVisit;

    VisitCashierAdapter(StartVisitUseCase startVisit) {
        this.startVisit = startVisit;
    }

    @Override
    public void visitStarted(UUID visitId, UUID patientId, boolean emergency, BigDecimal amount, Means means) {
        try {
            startVisit.handle(new StartVisitUseCase.Command(visitId, patientId, emergency, amount, means(means)));
        } catch (ChargeRuleViolationException e) {
            throw new ChargeRefusedException(switch (e.reason()) {
                case CHARGE_REQUIRED -> ChargeRefusedException.Reason.CHARGE_REQUIRED;
                case INVALID_AMOUNT -> ChargeRefusedException.Reason.INVALID_AMOUNT;
                case AMOUNT_TOO_HIGH -> ChargeRefusedException.Reason.AMOUNT_TOO_HIGH;
                // A visit starts with no charge, so nothing else of its charges can be refused yet.
                case ALREADY_CHARGED, NOTHING_TO_REFUND, REFUND_TOO_HIGH, REASON_REQUIRED, REASON_TOO_LONG,
                        CHANGED_MEANWHILE -> throw new IllegalStateException("A visit starting was refused so", e);
            }, e.getMessage(), e.limit());
        }
    }

    private static com.clinic.schedule.cashdesk.domain.Means means(Means means) {
        return means == null ? null : switch (means) {
            case CASH -> com.clinic.schedule.cashdesk.domain.Means.CASH;
            case DEBIT -> com.clinic.schedule.cashdesk.domain.Means.DEBIT;
            case CREDIT -> com.clinic.schedule.cashdesk.domain.Means.CREDIT;
            case TRANSFER -> com.clinic.schedule.cashdesk.domain.Means.TRANSFER;
            case QR -> com.clinic.schedule.cashdesk.domain.Means.QR;
        };
    }
}
