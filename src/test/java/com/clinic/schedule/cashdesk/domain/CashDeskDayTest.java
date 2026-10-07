package com.clinic.schedule.cashdesk.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.clinic.schedule.cashdesk.domain.CashDeskDay.Kind;
import com.clinic.schedule.cashdesk.domain.CashDeskDay.Movement;
import com.clinic.schedule.cashdesk.domain.CashDeskDay.RecorderTotal;
import com.clinic.schedule.cashdesk.domain.CashDeskDay.Sum;
import com.clinic.schedule.cashdesk.domain.CashDeskDay.Total;

class CashDeskDayTest {

    private static final LocalDate DAY = LocalDate.of(2026, 3, 10);

    private static Amount pesos(String value) {
        return new Amount(new BigDecimal(value));
    }

    private static Visit charged(String amount, String by, int hour) {
        return Visit.arrive(UUID.randomUUID(), UUID.randomUUID(), false, Optional.of(pesos(amount)), by,
                DAY.atTime(hour, 0));
    }

    private static Visit pending(int hour) {
        return Visit.arrive(UUID.randomUUID(), UUID.randomUUID(), true, Optional.empty(), "recepcion",
                DAY.atTime(hour, 0));
    }

    private static Sum sum(int count, String amount) {
        return new Sum(count, new BigDecimal(amount));
    }

    @Test
    void theDayAddsUpWhatWasChargedAndGivenBackOverallAndByWhoRecordedIt() {
        Visit refunded = charged("15000", "recepcion", 9).refund(pesos("5000"), "cobrado de más", "otra",
                DAY.atTime(12, 0));
        CashDeskDay day = new CashDeskDay(DAY, List.of(refunded, charged("8000.50", "otra", 10),
                charged("1000", "recepcion", 11)), List.of());

        assertThat(day.total()).isEqualTo(new Total(sum(3, "24000.50"), sum(1, "5000.00")));
        assertThat(day.total().left()).isEqualByComparingTo("19000.50");
        assertThat(day.byRecorder()).containsExactly(
                new RecorderTotal("otra", new Total(sum(1, "8000.50"), sum(1, "5000.00"))),
                new RecorderTotal("recepcion", new Total(sum(2, "16000.00"), sum(0, "0.00"))));
        assertThat(day.byRecorder().getFirst().total().left()).isEqualByComparingTo("3000.50");
    }

    @Test
    void aDayWithNothingRecordedAddsUpToZero() {
        CashDeskDay day = new CashDeskDay(DAY, List.of(), List.of(pending(9)));

        assertThat(day.total()).isEqualTo(new Total(sum(0, "0.00"), sum(0, "0.00")));
        assertThat(day.byRecorder()).isEmpty();
        assertThat(day.movements()).isEmpty();
    }

    @Test
    void theMovementsAreThoseRecordedThatDayInTheOrderTheyWere() {
        Visit yesterday = Visit.arrive(UUID.randomUUID(), UUID.randomUUID(), false, Optional.of(pesos("2000")),
                        "recepcion", DAY.minusDays(1).atTime(18, 0))
                .refund(pesos("2000"), "importe equivocado", "recepcion", DAY.atTime(9, 30))
                .charge(pesos("200"), "recepcion", DAY.atTime(9, 31))
                .refund(pesos("50"), "cobrado de más", "recepcion", DAY.plusDays(1).atTime(9, 0));
        Visit early = charged("1", "recepcion", 8);
        Visit late = charged("2", "recepcion", 12);

        CashDeskDay day = new CashDeskDay(DAY, List.of(late, yesterday, early), List.of());

        assertThat(day.movements()).extracting(Movement::kind, Movement::recordedAt, movement -> movement.amount().value())
                .containsExactly(
                        tuple(Kind.CHARGE, DAY.atTime(8, 0), new BigDecimal("1.00")),
                        tuple(Kind.REFUND, DAY.atTime(9, 30), new BigDecimal("2000.00")),
                        tuple(Kind.CHARGE, DAY.atTime(9, 31), new BigDecimal("200.00")),
                        tuple(Kind.CHARGE, DAY.atTime(12, 0), new BigDecimal("2.00")));
        Movement refund = day.movements().get(1);
        assertThat(refund.reason()).isEqualTo("importe equivocado");
        assertThat(refund.visitId()).isEqualTo(yesterday.id());
        assertThat(refund.means()).isEqualTo(Means.CASH);
        assertThat(refund.charge()).isEqualTo(yesterday.charges().getFirst());
        assertThat(day.movements().getFirst().reason()).isNull();
        assertThat(day.total().left()).isEqualByComparingTo("-1797.00");
    }

    @Test
    void thePendingVisitsComeInTheOrderTheyArrivedAndWereNeverCharged() {
        CashDeskDay day = new CashDeskDay(DAY, List.of(), List.of(pending(11), pending(6)));

        assertThat(day.pending()).extracting(Visit::arrivedAt).containsExactly(DAY.atTime(6, 0), DAY.atTime(11, 0));
        assertThatThrownBy(() -> new CashDeskDay(DAY, List.of(), List.of(charged("1", "recepcion", 9))))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
