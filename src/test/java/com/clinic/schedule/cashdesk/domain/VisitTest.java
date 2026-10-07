package com.clinic.schedule.cashdesk.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.clinic.schedule.cashdesk.domain.exception.ChargeRuleViolationException;
import com.clinic.schedule.cashdesk.domain.exception.ChargeRuleViolationException.Reason;

class VisitTest {

    private static final LocalDateTime NINE = LocalDateTime.of(2026, 3, 10, 9, 0);
    private static final LocalDateTime TEN = LocalDateTime.of(2026, 3, 10, 10, 0);
    private static final Amount FIFTEEN_THOUSAND = new Amount(new BigDecimal("15000"));

    private final UUID id = UUID.randomUUID();
    private final UUID patient = UUID.randomUUID();

    @Test
    void aVisitIsChargedAsItStarts() {
        Visit visit = Visit.arrive(id, patient, false, Optional.of(FIFTEEN_THOUSAND), "recepcion", NINE);

        assertThat(visit.pending()).isFalse();
        Charge charge = visit.charge().orElseThrow();
        assertThat(charge.amount()).isEqualTo(FIFTEEN_THOUSAND);
        assertThat(charge.means()).isEqualTo(Means.CASH);
        assertThat(charge.recordedBy()).isEqualTo("recepcion");
        assertThat(charge.recordedAt()).isEqualTo(NINE);
        assertThat(visit.arrivedAt()).isEqualTo(NINE);
    }

    @Test
    void withoutBeingChargedOnlyAnEmergencyStarts() {
        assertThatThrownBy(() -> Visit.arrive(id, patient, false, Optional.empty(), "recepcion", NINE))
                .isInstanceOfSatisfying(ChargeRuleViolationException.class,
                        e -> assertThat(e.reason()).isEqualTo(Reason.CHARGE_REQUIRED));

        Visit emergency = Visit.arrive(id, patient, true, Optional.empty(), "recepcion", NINE);

        assertThat(emergency.pending()).isTrue();
        assertThat(emergency.charge()).isEmpty();
    }

    @Test
    void anEmergencyMayBeChargedAsItStarts() {
        Visit emergency = Visit.arrive(id, patient, true, Optional.of(FIFTEEN_THOUSAND), "recepcion", NINE);

        assertThat(emergency.pending()).isFalse();
        assertThat(emergency.emergency()).isTrue();
    }

    @Test
    void anEmergencysPendingChargeIsRecordedLaterByWhoeverChargesIt() {
        Visit pending = Visit.arrive(id, patient, true, Optional.empty(), "recepcion", NINE);

        Visit charged = pending.charge(FIFTEEN_THOUSAND, "otra", TEN);

        assertThat(charged.charge().orElseThrow().recordedBy()).isEqualTo("otra");
        assertThat(charged.charge().orElseThrow().recordedAt()).isEqualTo(TEN);
        assertThat(charged.arrivedAt()).isEqualTo(NINE);
        assertThat(charged.id()).isEqualTo(id);
        assertThat(charged.patientId()).isEqualTo(patient);
        assertThat(pending.pending()).isTrue();
    }

    @Test
    void aVisitIsChargedOnce() {
        Visit charged = Visit.arrive(id, patient, false, Optional.of(FIFTEEN_THOUSAND), "recepcion", NINE);

        assertThatThrownBy(() -> charged.charge(FIFTEEN_THOUSAND, "recepcion", TEN))
                .isInstanceOfSatisfying(ChargeRuleViolationException.class,
                        e -> assertThat(e.reason()).isEqualTo(Reason.ALREADY_CHARGED));
    }

    @Test
    void aVisitReadBackObeysTheSameRules() {
        assertThatThrownBy(() -> Visit.recorded(id, patient, NINE, false, null))
                .isInstanceOf(ChargeRuleViolationException.class);
    }

    @Test
    void aChargeSaysWhoRecordedIt() {
        assertThatThrownBy(() -> Visit.arrive(id, patient, false, Optional.of(FIFTEEN_THOUSAND), " ", NINE))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
