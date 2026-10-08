package com.clinic.schedule.cashdesk.application.usecase;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.clinic.schedule.cashdesk.application.port.StaffUserPort;
import com.clinic.schedule.cashdesk.application.port.VisitRepository;
import com.clinic.schedule.cashdesk.domain.Amount;
import com.clinic.schedule.cashdesk.domain.AmountCeiling;
import com.clinic.schedule.cashdesk.domain.Means;
import com.clinic.schedule.cashdesk.domain.Visit;

/**
 * Reception put a patient into a queue: a visit starts, charged in the same step, or left pending when it
 * is an emergency (D-87). Called by the queues, inside their transaction, so a refused charge keeps the
 * patient out of the queue. A charge whose means is not said is cash, as part 1 had it (D-89).
 */
@Service
public class StartVisitUseCase {

    private final VisitRepository visits;
    private final StaffUserPort currentUser;
    private final AmountCeiling ceiling;
    private final Clock clock;

    StartVisitUseCase(VisitRepository visits, StaffUserPort currentUser, AmountCeiling ceiling, Clock clock) {
        this.visits = visits;
        this.currentUser = currentUser;
        this.ceiling = ceiling;
        this.clock = clock;
    }

    /** @throws com.clinic.schedule.cashdesk.domain.exception.ChargeRuleViolationException when it cannot start */
    @Transactional
    public void handle(Command command) {
        Optional<Amount> amount = Optional.ofNullable(command.amount()).map(typed -> Amount.typed(typed, ceiling));
        visits.save(Visit.arrive(command.visitId(), command.patientId(), command.emergency(), amount,
                command.means() != null ? command.means() : Means.CASH, currentUser.username(),
                LocalDateTime.now(clock)));
    }

    /**
     * @param amount what reception charged; null for an emergency left pending
     * @param means how it was paid; null for cash
     */
    public record Command(UUID visitId, UUID patientId, boolean emergency, BigDecimal amount, Means means) {
    }
}
