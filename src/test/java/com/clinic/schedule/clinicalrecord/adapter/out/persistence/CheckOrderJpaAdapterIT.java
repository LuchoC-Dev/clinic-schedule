package com.clinic.schedule.clinicalrecord.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import com.clinic.schedule.clinicalrecord.domain.Check;
import com.clinic.schedule.clinicalrecord.domain.CheckId;
import com.clinic.schedule.clinicalrecord.domain.CheckOrder;
import com.clinic.schedule.clinicalrecord.domain.CheckOrderId;
import com.clinic.schedule.clinicalrecord.domain.Consultation;
import com.clinic.schedule.clinicalrecord.domain.ConsultationId;
import com.clinic.schedule.clinicalrecord.domain.OrderOutcome;
import com.clinic.schedule.clinicalrecord.domain.PatientId;
import com.clinic.schedule.clinicalrecord.domain.PractitionerId;
import com.clinic.schedule.clinicalrecord.domain.exception.OrderNotOpenException;

/**
 * What only the storage can show: two checks fulfilling one order at the same time do not both
 * succeed — the second is refused (D-55). The second transaction is opened inside the first, so
 * both read the order while it is still open.
 */
@SpringBootTest
class CheckOrderJpaAdapterIT {

    private static final LocalDateTime AT = LocalDateTime.of(2026, 3, 10, 10, 15);

    @Autowired
    private CheckOrderJpaAdapter orders;

    @Autowired
    private CheckJpaAdapter checks;

    @Autowired
    private ConsultationJpaAdapter consultations;

    @Autowired
    private PlatformTransactionManager transactions;

    private final PatientId patient = new PatientId(UUID.randomUUID());
    private final PractitionerId arias = new PractitionerId(UUID.randomUUID());

    private TransactionTemplate inTransaction(int propagation) {
        TransactionTemplate template = new TransactionTemplate(transactions);
        template.setPropagationBehavior(propagation);
        return template;
    }

    private Check aCheck(String description) {
        Check check = Check.perform(CheckId.newId(), patient, arias, AT.plusHours(1), description, null);
        inTransaction(TransactionDefinition.PROPAGATION_REQUIRES_NEW).executeWithoutResult(status -> checks.save(check));
        return check;
    }

    @Test
    void twoChecksAtTheSameTimeDoNotBothFulfilAnOrder() {
        Consultation consultation = Consultation.record(ConsultationId.newId(), patient, arias, AT, "Control.", List.of());
        CheckOrder open = CheckOrder.issue(CheckOrderId.newId(), patient, arias, AT, consultation.id(), "Hemograma");
        inTransaction(TransactionDefinition.PROPAGATION_REQUIRED).executeWithoutResult(status -> {
            consultations.save(consultation);
            orders.save(open);
        });
        Check first = aCheck("Extracción de la mañana");
        Check second = aCheck("Extracción de la tarde");

        assertThatThrownBy(() -> inTransaction(TransactionDefinition.PROPAGATION_REQUIRED).executeWithoutResult(slow -> {
            CheckOrder readBySlow = orders.findById(open.id()).orElseThrow();

            inTransaction(TransactionDefinition.PROPAGATION_REQUIRES_NEW).executeWithoutResult(fast -> {
                CheckOrder readByFast = orders.findById(open.id()).orElseThrow();
                orders.save(readByFast.fulfilledBy(first));
            });

            orders.save(readBySlow.fulfilledBy(second));
        })).isInstanceOf(OrderNotOpenException.class);

        assertThat(orders.findById(open.id()).orElseThrow().outcome()).contains(new OrderOutcome.Fulfilled(first.id()));
    }
}
