package com.clinic.schedule.cashdesk.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.clinic.schedule.cashdesk.domain.CashDeskDay.RecorderTotal;
import com.clinic.schedule.cashdesk.domain.CashDeskDay.Total;

class CashDeskDayTest {

    private static final LocalDate DAY = LocalDate.of(2026, 3, 10);

    private static Visit charged(String amount, String by, int hour) {
        return Visit.arrive(UUID.randomUUID(), UUID.randomUUID(), false, Optional.of(new Amount(new BigDecimal(amount))),
                by, DAY.atTime(hour, 0));
    }

    private static Visit pending(int hour) {
        return Visit.arrive(UUID.randomUUID(), UUID.randomUUID(), true, Optional.empty(), "recepcion",
                DAY.atTime(hour, 0));
    }

    @Test
    void theDayAddsUpWhatWasChargedOverallAndByWhoRecordedIt() {
        CashDeskDay day = new CashDeskDay(DAY, List.of(charged("15000", "recepcion", 9), charged("8000.50", "otra", 10),
                charged("1000", "recepcion", 11)), List.of());

        assertThat(day.total()).isEqualTo(new Total(3, new BigDecimal("24000.50")));
        assertThat(day.byRecorder()).containsExactly(
                new RecorderTotal("otra", new Total(1, new BigDecimal("8000.50"))),
                new RecorderTotal("recepcion", new Total(2, new BigDecimal("16000.00"))));
    }

    @Test
    void aDayWithNothingChargedAddsUpToZero() {
        CashDeskDay day = new CashDeskDay(DAY, List.of(), List.of(pending(9)));

        assertThat(day.total()).isEqualTo(new Total(0, new BigDecimal("0.00")));
        assertThat(day.byRecorder()).isEmpty();
    }

    @Test
    void theChargesAndThePendingVisitsComeInTheOrderTheyHappened() {
        Visit early = charged("1", "recepcion", 8);
        Visit late = charged("2", "recepcion", 12);
        Visit pendingEarly = pending(7);
        Visit pendingLate = pending(11);
        Visit chargedLater = pendingEarly.charge(new Amount(BigDecimal.TEN), "recepcion", DAY.atTime(10, 0));

        CashDeskDay day = new CashDeskDay(DAY, List.of(late, chargedLater, early), List.of(pendingLate, pending(6)));

        assertThat(day.charged()).containsExactly(early, chargedLater, late);
        assertThat(day.pending()).extracting(Visit::arrivedAt).containsExactly(DAY.atTime(6, 0), DAY.atTime(11, 0));
    }

    @Test
    void theChargedHaveTheirChargeAndThePendingDoNot() {
        assertThatThrownBy(() -> new CashDeskDay(DAY, List.of(pending(9)), List.of()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new CashDeskDay(DAY, List.of(), List.of(charged("1", "recepcion", 9))))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
