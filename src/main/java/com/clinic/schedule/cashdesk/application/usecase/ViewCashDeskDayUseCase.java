package com.clinic.schedule.cashdesk.application.usecase;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.clinic.schedule.cashdesk.application.port.VisitRepository;
import com.clinic.schedule.cashdesk.domain.CashDeskDay;

/**
 * A day of the cash desk, today unless another is asked for (D-87, D-88, D-90). Reception and the admins read it,
 * each through a route of their own that says what they see (D-34): the use case hands it over whole, and
 * the admins' adapters leave the patients, the pending visits and the covered ones out.
 */
@Service
public class ViewCashDeskDayUseCase {

    private final VisitRepository visits;
    private final Clock clock;

    ViewCashDeskDayUseCase(VisitRepository visits, Clock clock) {
        this.visits = visits;
        this.clock = clock;
    }

    /** @param date the day; null for today */
    @Transactional(readOnly = true)
    public CashDeskDay handle(LocalDate date) {
        LocalDate day = date != null ? date : LocalDate.now(clock);
        LocalDateTime from = day.atStartOfDay();
        LocalDateTime until = day.plusDays(1).atStartOfDay();
        return new CashDeskDay(day, visits.findWithMovementsBetween(from, until),
                visits.findPendingArrivedBetween(from, until), visits.findCoveredBetween(from, until));
    }
}
