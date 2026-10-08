package com.clinic.schedule.cashdesk.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.clinic.schedule.cashdesk.domain.exception.ChargeRuleViolationException;
import com.clinic.schedule.cashdesk.domain.exception.ChargeRuleViolationException.Reason;

class VisitTest {

    private static final LocalDateTime NINE = LocalDateTime.of(2026, 3, 10, 9, 0);
    private static final LocalDateTime TEN = LocalDateTime.of(2026, 3, 10, 10, 0);
    private static final LocalDateTime ELEVEN = LocalDateTime.of(2026, 3, 10, 11, 0);
    private static final Amount FIFTEEN_THOUSAND = new Amount(new BigDecimal("15000"));

    /** No coverage: paid privately. */
    private static final Optional<Coverage> PRIVATE = Optional.empty();

    private final UUID id = UUID.randomUUID();
    private final UUID patient = UUID.randomUUID();

    private Visit charged() {
        return Visit.arrive(id, patient, false, PRIVATE, Optional.of(FIFTEEN_THOUSAND), Means.CASH, "recepcion", NINE);
    }

    private static Amount pesos(String value) {
        return new Amount(new BigDecimal(value));
    }

    private static void refused(Runnable action, Reason reason) {
        assertThatThrownBy(action::run).isInstanceOfSatisfying(ChargeRuleViolationException.class,
                e -> assertThat(e.reason()).isEqualTo(reason));
    }

    @Test
    void aVisitIsChargedAsItStarts() {
        Visit visit = charged();

        assertThat(visit.pending()).isFalse();
        Charge charge = visit.current().orElseThrow();
        assertThat(visit.charges()).containsExactly(charge);
        assertThat(charge.amount()).isEqualTo(FIFTEEN_THOUSAND);
        assertThat(charge.means()).isEqualTo(Means.CASH);
        assertThat(charge.recordedBy()).isEqualTo("recepcion");
        assertThat(charge.recordedAt()).isEqualTo(NINE);
        assertThat(charge.refunds()).isEmpty();
        assertThat(charge.left()).isEqualByComparingTo("15000");
        assertThat(visit.arrivedAt()).isEqualTo(NINE);
    }

    @Test
    void withoutBeingChargedOnlyAnEmergencyStarts() {
        refused(() -> Visit.arrive(id, patient, false, PRIVATE, Optional.empty(), Means.CASH, "recepcion", NINE),
                Reason.CHARGE_REQUIRED);

        Visit emergency = Visit.arrive(id, patient, true, PRIVATE, Optional.empty(), Means.CASH, "recepcion", NINE);

        assertThat(emergency.pending()).isTrue();
        assertThat(emergency.charges()).isEmpty();
        assertThat(emergency.current()).isEmpty();
    }

    @Test
    void anEmergencyMayBeChargedAsItStarts() {
        Visit emergency = Visit.arrive(id, patient, true, PRIVATE, Optional.of(FIFTEEN_THOUSAND), Means.CASH, "recepcion",
                NINE);

        assertThat(emergency.pending()).isFalse();
        assertThat(emergency.emergency()).isTrue();
    }

    @Test
    void anEmergencysPendingChargeIsRecordedLaterByWhoeverChargesIt() {
        Visit pending = Visit.arrive(id, patient, true, PRIVATE, Optional.empty(), Means.CASH, "recepcion", NINE);

        Visit charged = pending.charge(FIFTEEN_THOUSAND, Means.CASH, "otra", TEN);

        assertThat(charged.current().orElseThrow().recordedBy()).isEqualTo("otra");
        assertThat(charged.current().orElseThrow().recordedAt()).isEqualTo(TEN);
        assertThat(charged.arrivedAt()).isEqualTo(NINE);
        assertThat(charged.id()).isEqualTo(id);
        assertThat(charged.patientId()).isEqualTo(patient);
        assertThat(pending.pending()).isTrue();
    }

    @Test
    void aVisitWithSomethingLeftOfItsChargeIsNotChargedAgain() {
        refused(() -> charged().charge(FIFTEEN_THOUSAND, Means.CASH, "recepcion", TEN), Reason.ALREADY_CHARGED);
        Visit partly = charged().refund(pesos("5000"), "cobrado de más", "recepcion", TEN);
        refused(() -> partly.charge(FIFTEEN_THOUSAND, Means.CASH, "recepcion", ELEVEN), Reason.ALREADY_CHARGED);
    }

    @Test
    void someOfAChargeIsGivenBackAsAMovementOfItsOwn() {
        Visit visit = charged();

        Visit after = visit.refund(pesos("5000"), "  cobrado de más  ", "otra", TEN);

        Charge charge = after.current().orElseThrow();
        assertThat(charge.id()).isEqualTo(visit.current().orElseThrow().id());
        assertThat(charge.amount()).isEqualTo(FIFTEEN_THOUSAND);
        assertThat(charge.left()).isEqualByComparingTo("10000");
        Refund refund = charge.refunds().getFirst();
        assertThat(refund.amount()).isEqualTo(pesos("5000"));
        assertThat(refund.reason()).isEqualTo("cobrado de más");
        assertThat(refund.recordedBy()).isEqualTo("otra");
        assertThat(refund.recordedAt()).isEqualTo(TEN);
        assertThat(visit.current().orElseThrow().refunds()).isEmpty();
    }

    @Test
    void noMoreIsGivenBackThanIsLeft() {
        Visit partly = charged().refund(pesos("5000"), "cobrado de más", "recepcion", TEN);

        assertThatThrownBy(() -> partly.refund(pesos("10000.01"), "se fue", "recepcion", ELEVEN))
                .isInstanceOfSatisfying(ChargeRuleViolationException.class, e -> {
                    assertThat(e.reason()).isEqualTo(Reason.REFUND_TOO_HIGH);
                    assertThat(e.limit()).isEqualByComparingTo("10000");
                });

        Visit whole = partly.refund(pesos("10000"), "se fue sin ser atendido", "recepcion", ELEVEN);
        assertThat(whole.current()).isEmpty();
        assertThat(whole.charges().getFirst().refunds()).hasSize(2);
        assertThat(whole.pending()).isFalse();
    }

    @Test
    void nothingIsGivenBackOfAVisitWithNothingLeft() {
        refused(() -> Visit.arrive(id, patient, true, PRIVATE, Optional.empty(), Means.CASH, "recepcion", NINE)
                .refund(pesos("1"), "error", "recepcion", TEN), Reason.NOTHING_TO_REFUND);
        Visit whole = charged().refund(FIFTEEN_THOUSAND, "error", "recepcion", TEN);
        refused(() -> whole.refund(pesos("1"), "error", "recepcion", ELEVEN), Reason.NOTHING_TO_REFUND);
    }

    @Test
    void aVisitGivenBackWholeIsChargedAgain() {
        Visit whole = charged().refund(FIFTEEN_THOUSAND, "importe equivocado", "recepcion", TEN);

        Visit again = whole.charge(pesos("1500"), Means.CASH, "recepcion", ELEVEN);

        assertThat(again.charges()).hasSize(2);
        assertThat(again.current().orElseThrow().amount()).isEqualTo(pesos("1500"));
        assertThat(again.current().orElseThrow().recordedAt()).isEqualTo(ELEVEN);
        assertThat(again.charges().getFirst().left()).isEqualByComparingTo("0");
        assertThat(again.refund(pesos("1500"), "otra vez", "recepcion", ELEVEN).current()).isEmpty();
    }

    @Test
    void aVisitIsChargedByTheMeansReceptionSays() {
        Visit card = Visit.arrive(id, patient, false, PRIVATE, Optional.of(FIFTEEN_THOUSAND), Means.CREDIT, "recepcion",
                NINE);
        assertThat(card.current().orElseThrow().means()).isEqualTo(Means.CREDIT);

        Visit pending = Visit.arrive(id, patient, true, PRIVATE, Optional.empty(), Means.QR, "recepcion", NINE);
        assertThat(pending.charge(FIFTEEN_THOUSAND, Means.TRANSFER, "recepcion", TEN).current().orElseThrow().means())
                .isEqualTo(Means.TRANSFER);

        Visit again = card.refund(FIFTEEN_THOUSAND, "importe equivocado", "recepcion", TEN)
                .charge(pesos("1500"), Means.DEBIT, "recepcion", ELEVEN);
        assertThat(again.charges()).extracting(Charge::means).containsExactly(Means.CREDIT, Means.DEBIT);
    }

    @Test
    void aRefundSaysWhyInTwoHundredCharactersAtMost() {
        refused(() -> charged().refund(pesos("1"), "   ", "recepcion", TEN), Reason.REASON_REQUIRED);
        refused(() -> charged().refund(pesos("1"), null, "recepcion", TEN), Reason.REASON_REQUIRED);
        refused(() -> charged().refund(pesos("1"), "x".repeat(201), "recepcion", TEN), Reason.REASON_TOO_LONG);

        assertThat(charged().refund(pesos("1"), "x".repeat(200), "recepcion", TEN).current().orElseThrow()
                .refunds().getFirst().reason()).hasSize(200);
    }

    @Test
    void aVisitReadBackObeysTheSameRules() {
        refused(() -> Visit.recorded(id, patient, NINE, false, null, List.of()), Reason.CHARGE_REQUIRED);
        Charge open = charged().current().orElseThrow();
        assertThatThrownBy(() -> Visit.recorded(id, patient, NINE, false, null, List.of(open, open)))
                .isInstanceOf(IllegalArgumentException.class);
        Refund tooMuch = new Refund(UUID.randomUUID(), pesos("15000.01"), "error", "recepcion", TEN);
        assertThatThrownBy(() -> new Charge(UUID.randomUUID(), FIFTEEN_THOUSAND, Means.CASH, false, "recepcion", NINE,
                List.of(tooMuch))).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void aMovementSaysWhoRecordedIt() {
        assertThatThrownBy(() -> Visit.arrive(id, patient, false, PRIVATE, Optional.of(FIFTEEN_THOUSAND), Means.CASH,
                " ", NINE))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> charged().refund(pesos("1"), "error", " ", TEN))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
