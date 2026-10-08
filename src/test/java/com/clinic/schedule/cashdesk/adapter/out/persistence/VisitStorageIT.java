package com.clinic.schedule.cashdesk.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.support.TransactionTemplate;

import com.clinic.schedule.cashdesk.application.port.VisitRepository;
import com.clinic.schedule.cashdesk.application.usecase.ChargeVisitUseCase;
import com.clinic.schedule.cashdesk.domain.Amount;
import com.clinic.schedule.cashdesk.domain.Charge;
import com.clinic.schedule.cashdesk.domain.Coverage;
import com.clinic.schedule.cashdesk.domain.Insurance;
import com.clinic.schedule.cashdesk.domain.Insured;
import com.clinic.schedule.cashdesk.domain.Means;
import com.clinic.schedule.cashdesk.domain.Visit;
import com.clinic.schedule.cashdesk.domain.exception.ChargeRuleViolationException;
import com.clinic.schedule.cashdesk.domain.exception.ChargeRuleViolationException.Reason;

/**
 * What storage keeps of the visits, their charges and their refunds (D-87, D-88), and what it refuses when
 * two requests at once read the same visit: each lets its own change through, and the unique keys refuse the
 * second, as a visit charged already or one changed meanwhile, never as a 500.
 */
@SpringBootTest
class VisitStorageIT {

    /** No coverage: paid privately. */
    private static final Optional<Coverage> PRIVATE = Optional.empty();

    private static final LocalDateTime NINE = LocalDateTime.of(2026, 3, 10, 9, 0);
    /** A day no other test reads, for rows put in by hand that no visit could have. */
    private static final LocalDateTime FAR = LocalDateTime.of(2001, 1, 1, 9, 0);

    @Autowired
    private VisitRepository visits;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private TransactionTemplate transaction;

    @Autowired
    private ChargeVisitUseCase charge;

    private static Amount pesos(String value) {
        return new Amount(new BigDecimal(value));
    }

    private Visit keptPending() {
        Visit pending = Visit.arrive(UUID.randomUUID(), UUID.randomUUID(), true, PRIVATE, Optional.empty(), Means.CASH,
                "recepcion", NINE);
        visits.save(pending);
        return pending;
    }

    private Visit keptCharged(String amount) {
        Visit charged = Visit.arrive(UUID.randomUUID(), UUID.randomUUID(), false, PRIVATE, Optional.of(pesos(amount)),
                Means.CASH, "recepcion", NINE);
        visits.save(charged);
        return charged;
    }

    private void refused(Runnable action, Reason reason) {
        assertThatThrownBy(action::run).isInstanceOfSatisfying(ChargeRuleViolationException.class,
                e -> assertThat(e.reason()).isEqualTo(reason));
    }

    @Test
    void aVisitChargedMeanwhileIsRefusedAsChargedAlready() {
        Visit pending = keptPending();
        Visit first = pending.charge(pesos("5000"), Means.CASH, "recepcion", NINE.plusHours(1));
        Visit second = pending.charge(pesos("6000"), Means.CASH, "otra", NINE.plusHours(1));

        visits.save(first);

        refused(() -> visits.save(second), Reason.ALREADY_CHARGED);
        assertThat(visits.findById(pending.id()).orElseThrow().current().orElseThrow().amount())
                .isEqualTo(pesos("5000"));
    }

    @Test
    void aVisitChargedAgainMeanwhileIsRefusedAsChargedAlready() {
        Visit given = keptCharged("100").refund(pesos("100"), "importe equivocado", "recepcion", NINE.plusHours(1));
        visits.save(given);
        Visit first = given.charge(pesos("1000"), Means.CASH, "recepcion", NINE.plusHours(2));
        Visit second = given.charge(pesos("2000"), Means.CASH, "otra", NINE.plusHours(2));

        visits.save(first);

        refused(() -> visits.save(second), Reason.ALREADY_CHARGED);
        assertThat(visits.findById(given.id()).orElseThrow().charges()).hasSize(2);
    }

    @Test
    void aRefundGivenMeanwhileIsRefusedSoNoMoreIsGivenBackThanWasCharged() {
        Visit charged = keptCharged("100");
        Visit first = charged.refund(pesos("80"), "se fue", "recepcion", NINE.plusHours(1));
        Visit second = charged.refund(pesos("80"), "se fue", "otra", NINE.plusHours(1));

        visits.save(first);

        refused(() -> visits.save(second), Reason.CHANGED_MEANWHILE);
        assertThat(visits.findById(charged.id()).orElseThrow().current().orElseThrow().left())
                .isEqualByComparingTo("20");
    }

    @Test
    void aVisitIsReadBackWholeInTheOrderItsMovementsWereRecorded() {
        Visit visit = keptCharged("1234.5")
                .refund(pesos("34.5"), "cobrado de más", "recepcion", NINE.plusHours(1))
                .refund(pesos("1200"), "importe equivocado", "otra", NINE.plusHours(2))
                .charge(pesos("10"), Means.CASH, "recepcion", NINE.plusHours(3))
                .refund(pesos("1"), "error", "recepcion", NINE.plusHours(4));

        visits.save(visit);
        visits.save(visit);

        Visit read = visits.findById(visit.id()).orElseThrow();
        assertThat(read.patientId()).isEqualTo(visit.patientId());
        assertThat(read.arrivedAt()).isEqualTo(NINE);
        assertThat(read.emergency()).isFalse();
        assertThat(read.charges()).isEqualTo(visit.charges());
        assertThat(visits.findById(UUID.randomUUID())).isEmpty();
        assertThat(visits.findWithMovementsBetween(NINE.plusHours(4), NINE.plusHours(5)))
                .extracting(Visit::id).contains(visit.id());
        assertThat(visits.findWithMovementsBetween(NINE.plusHours(5), NINE.plusHours(6)))
                .extracting(Visit::id).doesNotContain(visit.id());
    }

    @Test
    void theDatabaseKeepsAChargeAboveZeroInAMeansItKnowsAndOneOfEachNumber() {
        Visit pending = keptPending();
        String insert = "insert into cash_charge (id, visit_id, charge_no, amount, means, copay, recorded_by, "
                + "recorded_at) values (?, ?, ?, ?, ?, false, 'recepcion', ?)";

        assertThatThrownBy(() -> jdbc.update(insert, UUID.randomUUID(), pending.id(), 1, BigDecimal.ZERO, "CASH", FAR))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update(insert, UUID.randomUUID(), pending.id(), 1, BigDecimal.ONE, "CARD", FAR))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update(insert, UUID.randomUUID(), UUID.randomUUID(), 1, BigDecimal.ONE, "CASH",
                FAR)).isInstanceOf(DataIntegrityViolationException.class);
        jdbc.update(insert, UUID.randomUUID(), pending.id(), 1, BigDecimal.ONE, "CASH", FAR);
        jdbc.update(insert, UUID.randomUUID(), pending.id(), 2, BigDecimal.ONE, "CASH", FAR);
        assertThatThrownBy(() -> jdbc.update(insert, UUID.randomUUID(), pending.id(), 2, BigDecimal.ONE, "CASH", FAR))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void eachMeansIsKeptAndReadBack() {
        for (Means means : Means.values()) {
            Visit visit = Visit.arrive(UUID.randomUUID(), UUID.randomUUID(), false, PRIVATE, Optional.of(pesos("10")),
                    means, "recepcion", NINE);
            visits.save(visit);

            assertThat(visits.findById(visit.id()).orElseThrow().current().orElseThrow().means()).isEqualTo(means);
        }
    }

    @Test
    void theDatabaseKnowsTheFiveMeans() {
        Visit pending = keptPending();
        String insert = "insert into cash_charge (id, visit_id, charge_no, amount, means, copay, recorded_by, "
                + "recorded_at) values (?, ?, ?, 1, ?, false, 'recepcion', ?)";
        int number = 1;
        for (String means : new String[] {"CASH", "DEBIT", "CREDIT", "TRANSFER", "QR"}) {
            jdbc.update(insert, UUID.randomUUID(), pending.id(), number++, means, FAR);
        }
        int next = number;
        assertThatThrownBy(() -> jdbc.update(insert, UUID.randomUUID(), pending.id(), next, "CHECK", FAR))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void theDatabaseKeepsARefundAboveZeroOfACharge() {
        Visit charged = keptCharged("100");
        UUID charge = charged.current().orElseThrow().id();
        String insert = "insert into cash_refund (id, charge_id, refund_no, amount, reason, recorded_by, recorded_at) "
                + "values (?, ?, ?, ?, 'error', 'recepcion', ?)";

        assertThatThrownBy(() -> jdbc.update(insert, UUID.randomUUID(), charge, 1, BigDecimal.ZERO, NINE))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update(insert, UUID.randomUUID(), UUID.randomUUID(), 1, BigDecimal.ONE, NINE))
                .isInstanceOf(DataIntegrityViolationException.class);
        jdbc.update(insert, UUID.randomUUID(), charge, 1, BigDecimal.ONE, NINE);
        assertThatThrownBy(() -> jdbc.update(insert, UUID.randomUUID(), charge, 1, BigDecimal.ONE, NINE))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    // --- coverage (D-90) ---------------------------------------------------------------------

    private static Coverage coverage(String token, String by) {
        return Coverage.claim(new Insured(true, new Insurance("OSDE", "61234567801", null)), token, by, NINE);
    }

    private Visit keptCovered(Optional<Amount> copay) {
        Visit covered = Visit.arrive(UUID.randomUUID(), UUID.randomUUID(), false,
                Optional.of(coverage("123", "recepcion")), copay, Means.DEBIT, "recepcion", NINE);
        visits.save(covered);
        return covered;
    }

    @Test
    void aCoveredVisitIsReadBackWithItsCoverageItsCopayAndItsWithdrawal() {
        Visit visit = keptCovered(Optional.of(pesos("2000")))
                .refund(pesos("2000"), "error", "recepcion", NINE.plusHours(1))
                .withdrawCoverage("no era de la obra social", "otra", NINE.plusHours(2))
                .charge(pesos("15000"), Means.CASH, "recepcion", NINE.plusHours(3));

        visits.save(visit);
        visits.save(visit);

        Visit read = visits.findById(visit.id()).orElseThrow();
        assertThat(read.coverage()).isEqualTo(visit.coverage());
        assertThat(read.coverage().orElseThrow().insurance().plan()).isNull();
        assertThat(read.coverage().orElseThrow().withdrawal().reason()).isEqualTo("no era de la obra social");
        assertThat(read.charges()).extracting(Charge::copay).containsExactly(true, false);
        // Held until the transaction ends, so it is read inside one.
        assertThat(transaction.execute(status -> visits.findByIdForUpdate(visit.id())).orElseThrow().coverage())
                .isEqualTo(visit.coverage());
        Optional<Visit> nobody = transaction.execute(status -> visits.findByIdForUpdate(UUID.randomUUID()));
        assertThat(nobody).isEmpty();
        assertThat(visits.findCoveredBetween(NINE, NINE.plusMinutes(1))).extracting(Visit::id).contains(visit.id());
        assertThat(visits.findCoveredBetween(NINE.plusMinutes(1), NINE.plusHours(5))).extracting(Visit::id)
                .doesNotContain(visit.id());
    }

    @Test
    void aVisitIsHeldWhileItIsWrittenSoAChargeWaitsForACoverageUnderWay() throws Exception {
        Visit pending = keptPending();
        CountDownLatch held = new CountDownLatch(1);
        AtomicReference<Throwable> failed = new AtomicReference<>();
        Thread coverer = new Thread(() -> {
            try {
                transaction.executeWithoutResult(status -> {
                    Visit visit = visits.findByIdForUpdate(pending.id()).orElseThrow();
                    held.countDown();
                    try {
                        Thread.sleep(300);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                    visits.save(visit.cover(coverage("999", "recepcion"), Optional.empty(), Means.CASH, "recepcion",
                            NINE));
                });
            } catch (Throwable e) {
                failed.set(e);
                held.countDown();
            }
        });
        coverer.start();
        held.await();
        SecurityContextHolder.getContext().setAuthentication(
                new TestingAuthenticationToken("otra", null, "ROLE_RECEPTION"));
        try {
            // Held by the coverage under way, the charge waits for it, and is then its copay.
            charge.handle(new ChargeVisitUseCase.Command(pending.id(), new BigDecimal("500"), null));
        } finally {
            SecurityContextHolder.clearContext();
        }
        coverer.join();

        assertThat(failed.get()).isNull();
        Visit read = visits.findById(pending.id()).orElseThrow();
        assertThat(read.covered()).isTrue();
        assertThat(read.charges()).extracting(Charge::copay).containsExactly(true);
    }

    @Test
    void aVisitCoveredMeanwhileIsRefusedAsCoveredAlready() {
        Visit pending = keptPending();
        Visit stillPending = keptPending();
        Visit first = pending.cover(coverage("111", "recepcion"), Optional.empty(), Means.CASH, "recepcion", NINE);
        Visit second = pending.cover(coverage("222", "otra"), Optional.empty(), Means.CASH, "otra", NINE);

        visits.save(first);

        refused(() -> visits.save(second), Reason.ALREADY_COVERED);
        assertThat(visits.findById(pending.id()).orElseThrow().coverage().orElseThrow().token()).isEqualTo("111");
        assertThat(visits.findPendingArrivedBetween(NINE, NINE.plusMinutes(1))).extracting(Visit::id)
                .doesNotContain(pending.id());
        assertThat(visits.findPendingArrivedBetween(NINE, NINE.plusMinutes(1))).extracting(Visit::id)
                .contains(stillPending.id());
    }

    @Test
    void aCoverageWithdrawnMeanwhileIsRefused() {
        Visit covered = keptCovered(Optional.empty());
        Visit first = covered.withdrawCoverage("error", "recepcion", NINE.plusHours(1));
        Visit second = covered.withdrawCoverage("otro error", "otra", NINE.plusHours(1));

        visits.save(first);

        refused(() -> visits.save(second), Reason.NOT_COVERED);
        assertThat(visits.findById(covered.id()).orElseThrow().coverage().orElseThrow().withdrawal().reason())
                .isEqualTo("error");
    }

    @Test
    void theDatabaseKeepsOneCoverageOfAVisitAndOneWithdrawalOfACoverage() {
        Visit pending = keptPending();
        String cover = "insert into cash_coverage (visit_id, insurer, recorded_by, recorded_at) "
                + "values (?, 'OSDE', 'recepcion', ?)";
        String withdraw = "insert into cash_coverage_withdrawal (visit_id, reason, recorded_by, recorded_at) "
                + "values (?, 'error', 'recepcion', ?)";

        assertThatThrownBy(() -> jdbc.update(cover, UUID.randomUUID(), FAR))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update(withdraw, pending.id(), FAR))
                .isInstanceOf(DataIntegrityViolationException.class);
        jdbc.update(cover, pending.id(), FAR);
        assertThatThrownBy(() -> jdbc.update(cover, pending.id(), FAR))
                .isInstanceOf(DataIntegrityViolationException.class);
        jdbc.update(withdraw, pending.id(), FAR);
        assertThatThrownBy(() -> jdbc.update(withdraw, pending.id(), FAR))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("insert into cash_coverage (visit_id, recorded_by, recorded_at) "
                + "values (?, 'recepcion', ?)", keptPending().id(), FAR))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
