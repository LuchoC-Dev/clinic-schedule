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

/** A visit covered by the patient's health insurance (D-90): who may go in covered, the copay and the withdrawal. */
class CoverageTest {

    private static final LocalDateTime NINE = LocalDateTime.of(2026, 3, 10, 9, 0);
    private static final LocalDateTime TEN = LocalDateTime.of(2026, 3, 10, 10, 0);
    private static final LocalDateTime ELEVEN = LocalDateTime.of(2026, 3, 10, 11, 0);
    private static final Insurance OSDE = new Insurance("OSDE", "61234567801", "310");
    private static final Insured VERIFIED = new Insured(true, OSDE);
    private static final Amount COPAY = new Amount(new BigDecimal("2000"));

    private final UUID id = UUID.randomUUID();
    private final UUID patient = UUID.randomUUID();

    private static Coverage claim(String token) {
        return Coverage.claim(VERIFIED, token, "recepcion", NINE);
    }

    private Visit covered(Optional<Amount> copay) {
        return Visit.arrive(id, patient, false, Optional.of(claim("123456")), copay, Means.DEBIT, "recepcion", NINE);
    }

    private Visit pendingEmergency() {
        return Visit.arrive(id, patient, true, Optional.empty(), Optional.empty(), Means.CASH, "recepcion", NINE);
    }

    private static void refused(Runnable action, Reason reason) {
        assertThatThrownBy(action::run).isInstanceOfSatisfying(ChargeRuleViolationException.class,
                e -> assertThat(e.reason()).isEqualTo(reason));
    }

    @Test
    void onlyAPatientWithAnInsuranceAndAVerifiedIdentityGoesInCovered() {
        refused(() -> Coverage.claim(new Insured(true, null), null, "recepcion", NINE), Reason.NOT_INSURED);
        refused(() -> Coverage.claim(new Insured(false, null), null, "recepcion", NINE), Reason.NOT_INSURED);
        refused(() -> Coverage.claim(new Insured(false, OSDE), null, "recepcion", NINE), Reason.IDENTITY_NOT_VERIFIED);

        Coverage coverage = claim(null);

        assertThat(coverage.insurance()).isEqualTo(OSDE);
        assertThat(coverage.recordedBy()).isEqualTo("recepcion");
        assertThat(coverage.recordedAt()).isEqualTo(NINE);
        assertThat(coverage.standing()).isTrue();
    }

    @Test
    void theTokenIsOptionalTrimmedAndFiftyCharactersAtMost() {
        assertThat(claim(null).token()).isNull();
        assertThat(claim("   ").token()).isNull();
        assertThat(claim(" 123456 ").token()).isEqualTo("123456");
        assertThat(claim("x".repeat(50)).token()).hasSize(50);
        refused(() -> claim("x".repeat(51)), Reason.TOKEN_TOO_LONG);
    }

    @Test
    void aCoveredVisitWithNoCopayMovesNoMoneyAndIsNotPending() {
        Visit visit = covered(Optional.empty());

        assertThat(visit.covered()).isTrue();
        assertThat(visit.charges()).isEmpty();
        assertThat(visit.current()).isEmpty();
        assertThat(visit.pending()).isFalse();
        assertThat(visit.coverage().orElseThrow().token()).isEqualTo("123456");
    }

    @Test
    void aCopayIsAChargeAsAnyOtherThatSaysItIsACopay() {
        Visit visit = covered(Optional.of(COPAY));

        Charge copay = visit.current().orElseThrow();
        assertThat(copay.copay()).isTrue();
        assertThat(copay.amount()).isEqualTo(COPAY);
        assertThat(copay.means()).isEqualTo(Means.DEBIT);
        Visit again = visit.refund(COPAY, "coseguro equivocado", "recepcion", TEN)
                .charge(new Amount(new BigDecimal("1500")), Means.CASH, "recepcion", ELEVEN);
        assertThat(again.current().orElseThrow().copay()).isTrue();
        assertThat(Visit.arrive(id, patient, false, Optional.empty(), Optional.of(COPAY), Means.CASH, "recepcion", NINE)
                .current().orElseThrow().copay()).isFalse();
    }

    @Test
    void anEmergencysPendingVisitIsCoveredLaterWithItsCopayOrNone() {
        Visit none = pendingEmergency().cover(claim(null), Optional.empty(), Means.CASH, "otra", TEN);
        assertThat(none.covered()).isTrue();
        assertThat(none.pending()).isFalse();
        assertThat(none.charges()).isEmpty();

        Visit withCopay = pendingEmergency().cover(Coverage.claim(VERIFIED, null, "otra", TEN), Optional.of(COPAY),
                Means.QR, "otra", TEN);
        Charge copay = withCopay.current().orElseThrow();
        assertThat(copay.copay()).isTrue();
        assertThat(copay.means()).isEqualTo(Means.QR);
        assertThat(copay.recordedBy()).isEqualTo("otra");
        assertThat(withCopay.arrivedAt()).isEqualTo(NINE);
        assertThat(withCopay.coverage().orElseThrow().recordedAt()).isEqualTo(TEN);
    }

    @Test
    void aVisitChargedOrCoveredAlreadyIsNotCoveredAgain() {
        refused(() -> covered(Optional.empty()).cover(claim(null), Optional.empty(), Means.CASH, "recepcion", TEN),
                Reason.ALREADY_COVERED);
        Visit charged = pendingEmergency().charge(COPAY, Means.CASH, "recepcion", TEN);
        refused(() -> charged.cover(claim(null), Optional.empty(), Means.CASH, "recepcion", TEN),
                Reason.ALREADY_CHARGED);
        Visit givenBack = charged.refund(COPAY, "error", "recepcion", TEN);
        refused(() -> givenBack.cover(claim(null), Optional.empty(), Means.CASH, "recepcion", ELEVEN),
                Reason.ALREADY_CHARGED);
    }

    @Test
    void aCoverageIsWithdrawnSayingWhyOnceNothingIsLeftOfItsCopay() {
        refused(() -> covered(Optional.of(COPAY)).withdrawCoverage("error", "recepcion", TEN), Reason.COPAY_LEFT);
        refused(() -> covered(Optional.empty()).withdrawCoverage("  ", "recepcion", TEN), Reason.REASON_REQUIRED);
        refused(() -> covered(Optional.empty()).withdrawCoverage("x".repeat(201), "recepcion", TEN),
                Reason.REASON_TOO_LONG);

        Visit withdrawn = covered(Optional.of(COPAY)).refund(COPAY, "error", "recepcion", TEN)
                .withdrawCoverage(" no era de la obra social ", "otra", ELEVEN);

        assertThat(withdrawn.covered()).isFalse();
        assertThat(withdrawn.pending()).isFalse();
        CoverageWithdrawal withdrawal = withdrawn.coverage().orElseThrow().withdrawal();
        assertThat(withdrawal.reason()).isEqualTo("no era de la obra social");
        assertThat(withdrawal.recordedBy()).isEqualTo("otra");
        assertThat(withdrawal.recordedAt()).isEqualTo(ELEVEN);
        assertThat(withdrawn.coverage().orElseThrow().insurance()).isEqualTo(OSDE);
        refused(() -> withdrawn.withdrawCoverage("otra vez", "recepcion", ELEVEN), Reason.NOT_COVERED);
        refused(() -> pendingEmergency().withdrawCoverage("error", "recepcion", TEN), Reason.NOT_COVERED);
    }

    @Test
    void aVisitWhoseCoverageWasWithdrawnIsChargedPrivately() {
        Visit withdrawn = covered(Optional.empty()).withdrawCoverage("error", "recepcion", TEN);

        Visit charged = withdrawn.charge(new Amount(new BigDecimal("15000")), Means.CASH, "recepcion", ELEVEN);

        assertThat(charged.current().orElseThrow().copay()).isFalse();
        refused(() -> withdrawn.cover(claim(null), Optional.empty(), Means.CASH, "recepcion", ELEVEN),
                Reason.ALREADY_COVERED);
    }

    @Test
    void aCoveredVisitReadBackNeedsNoCharge() {
        Visit read = Visit.recorded(id, patient, NINE, false, claim(null), List.of());

        assertThat(read.covered()).isTrue();
        assertThatThrownBy(() -> new Insurance(" ", null, null)).isInstanceOf(IllegalArgumentException.class);
    }
}
