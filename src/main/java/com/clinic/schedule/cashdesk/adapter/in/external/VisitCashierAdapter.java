package com.clinic.schedule.cashdesk.adapter.in.external;

import java.math.BigDecimal;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.clinic.schedule.cashdesk.api.ChargeRefusedException;
import com.clinic.schedule.cashdesk.api.VisitCashier;
import com.clinic.schedule.cashdesk.application.usecase.StartVisitUseCase;
import com.clinic.schedule.cashdesk.domain.exception.ChargeRuleViolationException;

/**
 * Answers this module's {@link VisitCashier} for the queues (D-87): it starts the visit through a use case,
 * in the caller's transaction, and turns this module's refusals into the api's own.
 */
@Component
class VisitCashierAdapter implements VisitCashier {

    private final StartVisitUseCase startVisit;

    VisitCashierAdapter(StartVisitUseCase startVisit) {
        this.startVisit = startVisit;
    }

    @Override
    public void visitStarted(UUID visitId, UUID patientId, boolean emergency, BigDecimal amount) {
        try {
            startVisit.handle(new StartVisitUseCase.Command(visitId, patientId, emergency, amount));
        } catch (ChargeRuleViolationException e) {
            throw new ChargeRefusedException(switch (e.reason()) {
                case CHARGE_REQUIRED -> ChargeRefusedException.Reason.CHARGE_REQUIRED;
                case INVALID_AMOUNT -> ChargeRefusedException.Reason.INVALID_AMOUNT;
                case AMOUNT_TOO_HIGH -> ChargeRefusedException.Reason.AMOUNT_TOO_HIGH;
                // A visit starts uncharged, so it cannot have been charged already.
                case ALREADY_CHARGED -> throw new IllegalStateException("A visit starting was charged already", e);
            }, e.getMessage(), e.ceiling());
        }
    }
}
