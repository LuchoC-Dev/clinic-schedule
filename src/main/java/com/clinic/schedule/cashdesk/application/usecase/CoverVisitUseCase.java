package com.clinic.schedule.cashdesk.application.usecase;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.clinic.schedule.cashdesk.application.exception.VisitNotFoundException;
import com.clinic.schedule.cashdesk.application.port.InsuredPatientPort;
import com.clinic.schedule.cashdesk.application.port.StaffUserPort;
import com.clinic.schedule.cashdesk.application.port.VisitRepository;
import com.clinic.schedule.cashdesk.domain.Amount;
import com.clinic.schedule.cashdesk.domain.AmountCeiling;
import com.clinic.schedule.cashdesk.domain.Coverage;
import com.clinic.schedule.cashdesk.domain.Means;
import com.clinic.schedule.cashdesk.domain.Visit;

/**
 * Reception records an emergency's pending visit as covered by the patient's health insurance, after it went in,
 * with its copay if the insurer asks for one (D-90). Paid privately instead, it is charged as before (D-87). The
 * visit is held meanwhile, so nobody charges it at the same time.
 */
@Service
public class CoverVisitUseCase {

    private final VisitRepository visits;
    private final InsuredPatientPort patients;
    private final StaffUserPort currentUser;
    private final AmountCeiling ceiling;
    private final Clock clock;

    CoverVisitUseCase(VisitRepository visits, InsuredPatientPort patients, StaffUserPort currentUser,
            AmountCeiling ceiling, Clock clock) {
        this.visits = visits;
        this.patients = patients;
        this.currentUser = currentUser;
        this.ceiling = ceiling;
        this.clock = clock;
    }

    /**
     * @throws VisitNotFoundException when no visit has that id
     * @throws com.clinic.schedule.cashdesk.domain.exception.ChargeRuleViolationException when the patient may
     *         not go in covered, the token or the copay is wrong, or the visit was charged or covered already
     */
    @Transactional
    public void handle(Command command) {
        Visit visit = visits.findByIdForUpdate(command.visitId())
                .orElseThrow(() -> new VisitNotFoundException(command.visitId()));
        String by = currentUser.username();
        LocalDateTime now = LocalDateTime.now(clock);
        Coverage coverage = Coverage.claim(patients.insuredOf(visit.patientId()), command.token(), by, now);
        Optional<Amount> copay = Optional.ofNullable(command.copay()).map(typed -> Amount.typed(typed, ceiling));
        visits.save(visit.cover(coverage, copay, command.means() != null ? command.means() : Means.CASH, by, now));
    }

    /**
     * @param token what the insurer's check gave for the visit; null when it asked for none
     * @param copay null when the insurer asks for none
     * @param means how the copay was paid; null for cash
     */
    public record Command(UUID visitId, String token, BigDecimal copay, Means means) {
    }
}
