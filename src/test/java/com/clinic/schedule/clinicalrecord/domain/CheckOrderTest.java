package com.clinic.schedule.clinicalrecord.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDateTime;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.clinic.schedule.clinicalrecord.domain.exception.NotTheOrderAuthorException;
import com.clinic.schedule.clinicalrecord.domain.exception.OrderNotOpenException;

class CheckOrderTest {

    private static final PatientId PATIENT = new PatientId(UUID.randomUUID());
    private static final PractitionerId ARIAS = new PractitionerId(UUID.randomUUID());
    private static final PractitionerId BENITEZ = new PractitionerId(UUID.randomUUID());
    private static final ConsultationId CONSULTATION = ConsultationId.newId();
    private static final LocalDateTime ORDERED = LocalDateTime.of(2026, 3, 10, 10, 15);

    private static CheckOrder open() {
        return CheckOrder.issue(CheckOrderId.newId(), PATIENT, ARIAS, ORDERED, CONSULTATION, "  Hemograma completo  ");
    }

    private static Check doneBy(PractitionerId who, PatientId patient, LocalDateTime at) {
        return Check.perform(CheckId.newId(), patient, who, at, "Extracción de sangre", null);
    }

    @Test
    void anOrderIsIssuedOpenInAConsultation() {
        CheckOrder order = open();

        assertThat(order.isOpen()).isTrue();
        assertThat(order.status()).isEqualTo(OrderStatus.OPEN);
        assertThat(order.description()).isEqualTo("Hemograma completo");
        assertThat(order.consultationId()).isEqualTo(CONSULTATION);
        assertThat(order.outcome()).isEmpty();
    }

    @Test
    void anyoneMayFulfilItWithACheckOfTheSamePatientDoneAfterIt() {
        Check check = doneBy(BENITEZ, PATIENT, ORDERED.plusDays(2));

        CheckOrder fulfilled = open().fulfilledBy(check);

        assertThat(fulfilled.status()).isEqualTo(OrderStatus.FULFILLED);
        assertThat(fulfilled.outcome()).contains(new OrderOutcome.Fulfilled(check.id()));
        assertThatIllegalArgumentException()
                .isThrownBy(() -> open().fulfilledBy(doneBy(BENITEZ, new PatientId(UUID.randomUUID()), ORDERED.plusDays(1))));
        assertThatIllegalArgumentException()
                .isThrownBy(() -> open().fulfilledBy(doneBy(BENITEZ, PATIENT, ORDERED.minusDays(1))));
    }

    @Test
    void oneCheckFulfilsItAndNothingElseDoes() {
        CheckOrder fulfilled = open().fulfilledBy(doneBy(BENITEZ, PATIENT, ORDERED.plusDays(1)));

        assertThatThrownBy(() -> fulfilled.fulfilledBy(doneBy(BENITEZ, PATIENT, ORDERED.plusDays(2))))
                .isInstanceOf(OrderNotOpenException.class);
        assertThatThrownBy(() -> fulfilled.cancelled(ARIAS, ORDERED.plusDays(3), "Ya no hace falta"))
                .isInstanceOf(OrderNotOpenException.class);
    }

    @Test
    void onlyItsAuthorCancelsItAndWithAReason() {
        CheckOrder cancelled = open().cancelled(ARIAS, ORDERED.plusDays(1), "  El paciente trajo uno reciente  ");

        assertThat(cancelled.status()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(cancelled.outcome())
                .contains(new OrderOutcome.Cancelled(ORDERED.plusDays(1), "El paciente trajo uno reciente"));
        assertThatThrownBy(() -> open().cancelled(BENITEZ, ORDERED.plusDays(1), "No es mía"))
                .isInstanceOf(NotTheOrderAuthorException.class);
        assertThatIllegalArgumentException().isThrownBy(() -> open().cancelled(ARIAS, ORDERED.plusDays(1), " "));
        assertThatIllegalArgumentException()
                .isThrownBy(() -> open().cancelled(ARIAS, ORDERED.plusDays(1), "a".repeat(501)));
        assertThatThrownBy(() -> cancelled.fulfilledBy(doneBy(BENITEZ, PATIENT, ORDERED.plusDays(2))))
                .isInstanceOf(OrderNotOpenException.class);
    }

    @Test
    void anOrderNeedsItsPartsAndADescriptionOfAtMostTwoThousandCharacters() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> CheckOrder.issue(CheckOrderId.newId(), PATIENT, ARIAS, ORDERED, null, "x"));
        assertThatIllegalArgumentException()
                .isThrownBy(() -> CheckOrder.issue(CheckOrderId.newId(), PATIENT, ARIAS, ORDERED, CONSULTATION, " "));
        assertThatIllegalArgumentException().isThrownBy(() -> CheckOrder.issue(CheckOrderId.newId(), PATIENT, ARIAS,
                ORDERED, CONSULTATION, "a".repeat(2_001)));
    }

    @Test
    void anOutcomeCarriesWhatItNeeds() {
        assertThatIllegalArgumentException().isThrownBy(() -> new OrderOutcome.Fulfilled(null));
        assertThatIllegalArgumentException().isThrownBy(() -> new OrderOutcome.Cancelled(ORDERED, null));
        assertThatIllegalArgumentException().isThrownBy(() -> new OrderOutcome.Cancelled(null, "motivo"));
        assertThatIllegalArgumentException().isThrownBy(() -> CheckOrder.restore(CheckOrderId.newId(), PATIENT, ARIAS,
                ORDERED, CONSULTATION, "x", new OrderOutcome.Cancelled(ORDERED.minusDays(1), "antes de emitirla")));
        assertThat(CheckOrder.restore(CheckOrderId.newId(), PATIENT, ARIAS, ORDERED, CONSULTATION, "x",
                new OrderOutcome.Fulfilled(CheckId.newId())).status()).isEqualTo(OrderStatus.FULFILLED);
    }
}
