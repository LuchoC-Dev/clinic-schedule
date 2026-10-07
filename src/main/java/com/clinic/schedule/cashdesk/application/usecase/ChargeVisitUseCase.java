package com.clinic.schedule.cashdesk.application.usecase;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.clinic.schedule.cashdesk.application.exception.VisitNotFoundException;
import com.clinic.schedule.cashdesk.application.port.StaffUserPort;
import com.clinic.schedule.cashdesk.application.port.VisitRepository;
import com.clinic.schedule.cashdesk.domain.Amount;
import com.clinic.schedule.cashdesk.domain.AmountCeiling;
import com.clinic.schedule.cashdesk.domain.Visit;
import com.clinic.schedule.cashdesk.domain.exception.ChargeRuleViolationException;
import com.clinic.schedule.cashdesk.domain.exception.ChargeRuleViolationException.Reason;

/** Reception records an emergency's pending charge, after it went in (D-87). */
@Service
public class ChargeVisitUseCase {

    private final VisitRepository visits;
    private final StaffUserPort currentUser;
    private final AmountCeiling ceiling;
    private final Clock clock;

    ChargeVisitUseCase(VisitRepository visits, StaffUserPort currentUser, AmountCeiling ceiling, Clock clock) {
        this.visits = visits;
        this.currentUser = currentUser;
        this.ceiling = ceiling;
        this.clock = clock;
    }

    /**
     * @throws VisitNotFoundException when no visit has that id
     * @throws com.clinic.schedule.cashdesk.domain.exception.ChargeRuleViolationException when the amount is
     *         wrong or the visit was charged already
     */
    @Transactional
    public void handle(Command command) {
        Visit visit = visits.findById(command.visitId()).orElseThrow(() -> new VisitNotFoundException(command.visitId()));
        if (command.amount() == null) {
            throw new ChargeRuleViolationException(Reason.CHARGE_REQUIRED, "A charge needs its amount");
        }
        visits.save(visit.charge(Amount.typed(command.amount(), ceiling), currentUser.username(),
                LocalDateTime.now(clock)));
    }

    public record Command(UUID visitId, BigDecimal amount) {
    }
}
