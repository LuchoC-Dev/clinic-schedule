package com.clinic.schedule.cashdesk.application.usecase;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.clinic.schedule.cashdesk.application.exception.VisitNotFoundException;
import com.clinic.schedule.cashdesk.application.port.StaffUserPort;
import com.clinic.schedule.cashdesk.application.port.VisitRepository;
import com.clinic.schedule.cashdesk.domain.Visit;

/**
 * Reception withdraws a coverage it marked by mistake, and says why (D-90): nothing is changed nor deleted, the
 * withdrawal is a fact of its own, and the visit is then charged privately. The visit is held meanwhile, so
 * nobody charges its copay at the same time.
 */
@Service
public class WithdrawCoverageUseCase {

    private final VisitRepository visits;
    private final StaffUserPort currentUser;
    private final Clock clock;

    WithdrawCoverageUseCase(VisitRepository visits, StaffUserPort currentUser, Clock clock) {
        this.visits = visits;
        this.currentUser = currentUser;
        this.clock = clock;
    }

    /**
     * @throws VisitNotFoundException when no visit has that id
     * @throws com.clinic.schedule.cashdesk.domain.exception.ChargeRuleViolationException when the reason is wrong,
     *         the visit has no coverage standing, or something is left of its copay
     */
    @Transactional
    public void handle(Command command) {
        Visit visit = visits.findByIdForUpdate(command.visitId())
                .orElseThrow(() -> new VisitNotFoundException(command.visitId()));
        visits.save(visit.withdrawCoverage(command.reason(), currentUser.username(), LocalDateTime.now(clock)));
    }

    public record Command(UUID visitId, String reason) {
    }
}
