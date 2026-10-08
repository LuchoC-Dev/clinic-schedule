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
import com.clinic.schedule.cashdesk.domain.CashDeskDay.MeansTotal;
import com.clinic.schedule.cashdesk.domain.CashDeskDay.Movement;
import com.clinic.schedule.cashdesk.domain.CashDeskDay.RecorderTotal;
import com.clinic.schedule.cashdesk.domain.CashDeskDay.Sum;
import com.clinic.schedule.cashdesk.domain.CashDeskDay.Total;

class CashDeskDayTest {

    private static final LocalDate DAY = LocalDate.of(2026, 3, 10);

    /** No coverage: paid privately. */
    private static final Optional<Coverage> PRIVATE = Optional.empty();

    private static Amount pesos(String value) {
        return new Amount(new BigDecimal(value));
    }

    private static Visit charged(String amount, String by, int hour) {
        return charged(amount, Means.CASH, by, hour);
    }

    private static Visit charged(String amount, Means means, String by, int hour) {
        return Visit.arrive(UUID.randomUUID(), UUID.randomUUID(), false, PRIVATE, Optional.of(pesos(amount)), means, by,
                DAY.atTime(hour, 0));
    }

    private static Visit pending(int hour) {
        return Visit.arrive(UUID.randomUUID(), UUID.randomUUID(), true, PRIVATE, Optional.empty(), Means.CASH,
                "recepcion", DAY.atTime(hour, 0));
    }

    private static Sum sum(int count, String amount) {
        return new Sum(count, new BigDecimal(amount));
    }

    /** All in cash, as part 1 had it. */
    private static Total inCash(Sum charged, Sum refunded) {
        return new Total(charged, refunded, List.of(new MeansTotal(Means.CASH, charged, refunded)));
    }

    @Test
    void theDayAddsUpWhatWasChargedAndGivenBackOverallAndByWhoRecordedIt() {
        Visit refunded = charged("15000", "recepcion", 9).refund(pesos("5000"), "cobrado de más", "otra",
                DAY.atTime(12, 0));
        CashDeskDay day = new CashDeskDay(DAY, List.of(refunded, charged("8000.50", "otra", 10),
                charged("1000", "recepcion", 11)), List.of(), List.of());

        assertThat(day.total()).isEqualTo(inCash(sum(3, "24000.50"), sum(1, "5000.00")));
        assertThat(day.total().left()).isEqualByComparingTo("19000.50");
        assertThat(day.byRecorder()).containsExactly(
                new RecorderTotal("otra", inCash(sum(1, "8000.50"), sum(1, "5000.00"))),
                new RecorderTotal("recepcion", inCash(sum(2, "16000.00"), sum(0, "0.00"))));
        assertThat(day.byRecorder().getFirst().total().left()).isEqualByComparingTo("3000.50");
    }

    @Test
    void theTotalsAreSplitByMeansAndOnlyWhatIsLeftInCashGoesIntoTheDrawer() {
        Visit card = charged("10000", Means.DEBIT, "recepcion", 9)
                .refund(pesos("4000"), "cobrado de más", "recepcion", DAY.atTime(12, 0));
        Visit cash = charged("3000", Means.CASH, "recepcion", 10)
                .refund(pesos("500"), "cobrado de más", "recepcion", DAY.atTime(12, 30));
        CashDeskDay day = new CashDeskDay(DAY, List.of(card, cash, charged("2000", Means.QR, "recepcion", 11),
                charged("700", Means.CASH, "recepcion", 11), charged("100", Means.CREDIT, "recepcion", 11),
                charged("50", Means.TRANSFER, "recepcion", 11)), List.of(), List.of());

        assertThat(day.total().byMeans()).containsExactly(
                new MeansTotal(Means.CASH, sum(2, "3700.00"), sum(1, "500.00")),
                new MeansTotal(Means.DEBIT, sum(1, "10000.00"), sum(1, "4000.00")),
                new MeansTotal(Means.CREDIT, sum(1, "100.00"), sum(0, "0.00")),
                new MeansTotal(Means.TRANSFER, sum(1, "50.00"), sum(0, "0.00")),
                new MeansTotal(Means.QR, sum(1, "2000.00"), sum(0, "0.00")));
        assertThat(day.total().byMeans().get(1).left()).isEqualByComparingTo("6000");
        assertThat(day.total().left()).isEqualByComparingTo("11350");
        assertThat(day.total().drawer()).isEqualByComparingTo("3200");
    }

    @Test
    void eachUserHasTheirOwnTotalsByMeansAndTheirOwnDrawer() {
        CashDeskDay day = new CashDeskDay(DAY, List.of(charged("1000", Means.CASH, "otra", 9),
                charged("5000", Means.CREDIT, "recepcion", 10), charged("200", Means.CASH, "recepcion", 11)),
                List.of(), List.of());

        RecorderTotal otra = day.byRecorder().getFirst();
        RecorderTotal recepcion = day.byRecorder().getLast();
        assertThat(otra.total().byMeans())
                .containsExactly(new MeansTotal(Means.CASH, sum(1, "1000.00"), sum(0, "0.00")));
        assertThat(otra.total().drawer()).isEqualByComparingTo("1000");
        assertThat(recepcion.total().byMeans()).containsExactly(
                new MeansTotal(Means.CASH, sum(1, "200.00"), sum(0, "0.00")),
                new MeansTotal(Means.CREDIT, sum(1, "5000.00"), sum(0, "0.00")));
        assertThat(recepcion.total().drawer()).isEqualByComparingTo("200");
    }

    @Test
    void aDayWithoutCashHasNothingInTheDrawer() {
        CashDeskDay day = new CashDeskDay(DAY, List.of(charged("1000", Means.TRANSFER, "recepcion", 9)), List.of(),
                List.of());

        assertThat(day.total().left()).isEqualByComparingTo("1000");
        assertThat(day.total().drawer()).isEqualByComparingTo("0");
    }

    @Test
    void aDayWithNothingRecordedAddsUpToZero() {
        CashDeskDay day = new CashDeskDay(DAY, List.of(), List.of(pending(9)), List.of());

        assertThat(day.total()).isEqualTo(new Total(sum(0, "0.00"), sum(0, "0.00"), List.of()));
        assertThat(day.total().drawer()).isEqualByComparingTo("0");
        assertThat(day.byRecorder()).isEmpty();
        assertThat(day.movements()).isEmpty();
    }

    @Test
    void theMovementsAreThoseRecordedThatDayInTheOrderTheyWere() {
        Visit yesterday = Visit.arrive(UUID.randomUUID(), UUID.randomUUID(), false, PRIVATE, Optional.of(pesos("2000")),
                        Means.CASH, "recepcion", DAY.minusDays(1).atTime(18, 0))
                .refund(pesos("2000"), "importe equivocado", "recepcion", DAY.atTime(9, 30))
                .charge(pesos("200"), Means.CASH, "recepcion", DAY.atTime(9, 31))
                .refund(pesos("50"), "cobrado de más", "recepcion", DAY.plusDays(1).atTime(9, 0));
        Visit early = charged("1", "recepcion", 8);
        Visit late = charged("2", "recepcion", 12);

        CashDeskDay day = new CashDeskDay(DAY, List.of(late, yesterday, early), List.of(), List.of());

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
        CashDeskDay day = new CashDeskDay(DAY, List.of(), List.of(pending(11), pending(6)), List.of());

        assertThat(day.pending()).extracting(Visit::arrivedAt).containsExactly(DAY.atTime(6, 0), DAY.atTime(11, 0));
        assertThatThrownBy(() -> new CashDeskDay(DAY, List.of(), List.of(charged("1", "recepcion", 9)), List.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static Visit covered(Optional<Amount> copay, Means means, int hour) {
        return Visit.arrive(UUID.randomUUID(), UUID.randomUUID(), false,
                Optional.of(Coverage.claim(new Insured(true, new Insurance("OSDE", null, null)), null, "recepcion",
                        DAY.atTime(hour, 0))),
                copay, means, "recepcion", DAY.atTime(hour, 0));
    }

    @Test
    void aCopayCountsAsAnyChargeAndACoveredVisitWithNoneAddsNothing() {
        Visit copay = covered(Optional.of(pesos("2000")), Means.DEBIT, 10);
        Visit none = covered(Optional.empty(), Means.CASH, 9);
        Visit privately = charged("1000", "recepcion", 11);
        CashDeskDay day = new CashDeskDay(DAY, List.of(copay, none, privately), List.of(), List.of(copay, none));

        assertThat(day.movements()).extracting(Movement::visitId, Movement::copay)
                .containsExactly(tuple(copay.id(), true), tuple(privately.id(), false));
        assertThat(day.total()).isEqualTo(new Total(sum(2, "3000.00"), sum(0, "0.00"), List.of(
                new MeansTotal(Means.CASH, sum(1, "1000.00"), sum(0, "0.00")),
                new MeansTotal(Means.DEBIT, sum(1, "2000.00"), sum(0, "0.00")))));
        assertThat(day.covered()).containsExactly(none, copay);
    }

    @Test
    void theCoveredVisitsAreThoseWithACoverageAndAreNotPending() {
        assertThatThrownBy(() -> new CashDeskDay(DAY, List.of(), List.of(), List.of(charged("1", "recepcion", 9))))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new CashDeskDay(DAY, List.of(), List.of(covered(Optional.empty(), Means.CASH, 9)),
                List.of())).isInstanceOf(IllegalArgumentException.class);
    }
}
