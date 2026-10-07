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
import com.clinic.schedule.cashdesk.domain.Visit;

/**
 * Reception gives back some or all of what is left of a visit's charge, and says why (D-88). The application
 * never decides it alone: a patient who paid and was not seen is owed their money, and reception records it.
 */
@Service
public class RefundVisitUseCase {

    private final VisitRepository visits;
    private final StaffUserPort currentUser;
    private final Clock clock;

    RefundVisitUseCase(VisitRepository visits, StaffUserPort currentUser, Clock clock) {
        this.visits = visits;
        this.currentUser = currentUser;
        this.clock = clock;
    }

    /**
     * @throws VisitNotFoundException when no visit has that id
     * @throws com.clinic.schedule.cashdesk.domain.exception.ChargeRuleViolationException when the amount or
     *         the reason is wrong, or there is not that much left to give back
     */
    @Transactional
    public void handle(Command command) {
        Visit visit = visits.findById(command.visitId()).orElseThrow(() -> new VisitNotFoundException(command.visitId()));
        visits.save(visit.refund(new Amount(command.amount()), command.reason(), currentUser.username(),
                LocalDateTime.now(clock)));
    }

    public record Command(UUID visitId, BigDecimal amount, String reason) {
    }
}
