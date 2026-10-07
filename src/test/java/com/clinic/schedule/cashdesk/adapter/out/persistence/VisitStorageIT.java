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
 * Two requests at once charging the same pending visit (D-87): each read it uncharged, so the domain lets
 * both through, and the unique key refuses the second as a visit charged already, never as a 500.
 */
@SpringBootTest
class VisitStorageIT {

    private static final LocalDateTime NINE = LocalDateTime.of(2026, 3, 10, 9, 0);

    @Autowired
    private VisitRepository visits;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void aVisitChargedMeanwhileIsRefusedAsChargedAlready() {
        Visit pending = Visit.arrive(UUID.randomUUID(), UUID.randomUUID(), true, Optional.empty(), "recepcion", NINE);
        visits.save(pending);
        Visit first = pending.charge(new Amount(new BigDecimal("5000")), "recepcion", NINE.plusHours(1));
        Visit second = pending.charge(new Amount(new BigDecimal("6000")), "otra", NINE.plusHours(1));

        visits.save(first);

        assertThatThrownBy(() -> visits.save(second))
                .isInstanceOfSatisfying(ChargeRuleViolationException.class,
                        e -> assertThat(e.reason()).isEqualTo(Reason.ALREADY_CHARGED));
        assertThat(visits.findById(pending.id()).orElseThrow().charge().orElseThrow().amount().value())
                .isEqualByComparingTo("5000");
        assertThat(jdbc.queryForObject("select count(*) from cash_charge where visit_id = ?", Integer.class,
                pending.id())).isOne();
    }

    @Test
    void theDatabaseKeepsAChargeAboveZeroAndInAMeansItKnows() {
        Visit pending = Visit.arrive(UUID.randomUUID(), UUID.randomUUID(), true, Optional.empty(), "recepcion", NINE);
        visits.save(pending);
        String insert = "insert into cash_charge (id, visit_id, amount, means, recorded_by, recorded_at) "
                + "values (?, ?, ?, ?, 'recepcion', ?)";

        assertThatThrownBy(() -> jdbc.update(insert, UUID.randomUUID(), pending.id(), BigDecimal.ZERO, "CASH", NINE))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update(insert, UUID.randomUUID(), pending.id(), BigDecimal.ONE, "CARD", NINE))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update(insert, UUID.randomUUID(), UUID.randomUUID(), BigDecimal.ONE, "CASH", NINE))
                .isInstanceOf(DataIntegrityViolationException.class);
        jdbc.update(insert, UUID.randomUUID(), pending.id(), BigDecimal.ONE, "CASH", NINE);
    }

    @Test
    void aVisitIsReadBackAsItWasKept() {
        Visit charged = Visit.arrive(UUID.randomUUID(), UUID.randomUUID(), false,
                Optional.of(new Amount(new BigDecimal("1234.5"))), "recepcion", NINE);

        visits.save(charged);
        visits.save(charged);

        Visit read = visits.findById(charged.id()).orElseThrow();
        assertThat(read.patientId()).isEqualTo(charged.patientId());
        assertThat(read.arrivedAt()).isEqualTo(NINE);
        assertThat(read.emergency()).isFalse();
        assertThat(read.charge()).isEqualTo(charged.charge());
        assertThat(visits.findById(UUID.randomUUID())).isEmpty();
    }
}
