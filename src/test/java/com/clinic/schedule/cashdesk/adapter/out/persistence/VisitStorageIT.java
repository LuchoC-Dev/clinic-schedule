package com.clinic.schedule.cashdesk.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

import com.clinic.schedule.cashdesk.application.port.VisitRepository;
import com.clinic.schedule.cashdesk.domain.Amount;
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

    private static final LocalDateTime NINE = LocalDateTime.of(2026, 3, 10, 9, 0);
    /** A day no other test reads, for rows put in by hand that no visit could have. */
    private static final LocalDateTime FAR = LocalDateTime.of(2001, 1, 1, 9, 0);

    @Autowired
    private VisitRepository visits;

    @Autowired
    private JdbcTemplate jdbc;

    private static Amount pesos(String value) {
        return new Amount(new BigDecimal(value));
    }

    private Visit keptPending() {
        Visit pending = Visit.arrive(UUID.randomUUID(), UUID.randomUUID(), true, Optional.empty(), "recepcion", NINE);
        visits.save(pending);
        return pending;
    }

    private Visit keptCharged(String amount) {
        Visit charged = Visit.arrive(UUID.randomUUID(), UUID.randomUUID(), false, Optional.of(pesos(amount)),
                "recepcion", NINE);
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
        Visit first = pending.charge(pesos("5000"), "recepcion", NINE.plusHours(1));
        Visit second = pending.charge(pesos("6000"), "otra", NINE.plusHours(1));

        visits.save(first);

        refused(() -> visits.save(second), Reason.ALREADY_CHARGED);
        assertThat(visits.findById(pending.id()).orElseThrow().current().orElseThrow().amount())
                .isEqualTo(pesos("5000"));
    }

    @Test
    void aVisitChargedAgainMeanwhileIsRefusedAsChargedAlready() {
        Visit given = keptCharged("100").refund(pesos("100"), "importe equivocado", "recepcion", NINE.plusHours(1));
        visits.save(given);
        Visit first = given.charge(pesos("1000"), "recepcion", NINE.plusHours(2));
        Visit second = given.charge(pesos("2000"), "otra", NINE.plusHours(2));

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
                .charge(pesos("10"), "recepcion", NINE.plusHours(3))
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
        String insert = "insert into cash_charge (id, visit_id, charge_no, amount, means, recorded_by, recorded_at) "
                + "values (?, ?, ?, ?, ?, 'recepcion', ?)";

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
}
