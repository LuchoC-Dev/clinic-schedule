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

import com.clinic.schedule.clinicalrecord.application.exception.ConsultationChangedException;
import com.clinic.schedule.clinicalrecord.domain.Addendum;
import com.clinic.schedule.clinicalrecord.domain.AddendumId;
import com.clinic.schedule.clinicalrecord.domain.Consultation;
import com.clinic.schedule.clinicalrecord.domain.ConsultationId;
import com.clinic.schedule.clinicalrecord.domain.ConsultationItem;
import com.clinic.schedule.clinicalrecord.domain.ItemKind;
import com.clinic.schedule.clinicalrecord.domain.PatientId;
import com.clinic.schedule.clinicalrecord.domain.PractitionerId;

/**
 * What only the storage can show: a consultation comes back as it was written, and two addenda
 * added at the same time do not overwrite each other (D-52). The second transaction is opened
 * inside the first, so both read the consultation before either writes.
 */
@SpringBootTest
class ConsultationJpaAdapterIT {

    private static final LocalDateTime AT = LocalDateTime.of(2026, 3, 10, 10, 15);

    @Autowired
    private ConsultationJpaAdapter adapter;

    @Autowired
    private PlatformTransactionManager transactions;

    private final PractitionerId arias = new PractitionerId(UUID.randomUUID());
    private final PractitionerId benitez = new PractitionerId(UUID.randomUUID());

    private Consultation stored() {
        Consultation consultation = Consultation.record(ConsultationId.newId(), new PatientId(UUID.randomUUID()),
                arias, AT, "Control.", List.of(
                        new ConsultationItem(ItemKind.CHECK, "presión 120/80"),
                        new ConsultationItem(ItemKind.PRESCRIPTION, "enalapril 10 mg")));
        inTransaction(TransactionDefinition.PROPAGATION_REQUIRED).executeWithoutResult(status -> adapter.save(consultation));
        return consultation;
    }

    private TransactionTemplate inTransaction(int propagation) {
        TransactionTemplate template = new TransactionTemplate(transactions);
        template.setPropagationBehavior(propagation);
        return template;
    }

    private static Addendum addendum(PractitionerId author, String text) {
        return new Addendum(AddendumId.newId(), author, AT.plusHours(1), text);
    }

    @Test
    void aConsultationComesBackWithItsItemsAndAddendaInOrder() {
        Consultation consultation = stored();
        inTransaction(TransactionDefinition.PROPAGATION_REQUIRED).executeWithoutResult(status -> {
            Consultation read = adapter.findById(consultation.id()).orElseThrow();
            adapter.save(read.withAddendum(addendum(arias, "Primera.")));
        });
        inTransaction(TransactionDefinition.PROPAGATION_REQUIRED).executeWithoutResult(status -> {
            Consultation read = adapter.findById(consultation.id()).orElseThrow();
            adapter.save(read.withAddendum(addendum(benitez, "Segunda.")));
        });

        Consultation read = adapter.findById(consultation.id()).orElseThrow();
        assertThat(read.items()).extracting(ConsultationItem::kind).containsExactly(ItemKind.CHECK, ItemKind.PRESCRIPTION);
        assertThat(read.addenda()).extracting(Addendum::text).containsExactly("Primera.", "Segunda.");
    }

    @Test
    void twoAddendaAtTheSameTimeDoNotOverwriteEachOther() {
        Consultation consultation = stored();

        assertThatThrownBy(() -> inTransaction(TransactionDefinition.PROPAGATION_REQUIRED).executeWithoutResult(slow -> {
            Consultation readBySlow = adapter.findById(consultation.id()).orElseThrow();

            // Another practitioner reads, adds and commits while the first one is still writing.
            inTransaction(TransactionDefinition.PROPAGATION_REQUIRES_NEW).executeWithoutResult(fast -> {
                Consultation readByFast = adapter.findById(consultation.id()).orElseThrow();
                adapter.save(readByFast.withAddendum(addendum(benitez, "La de Benítez.")));
            });

            adapter.save(readBySlow.withAddendum(addendum(arias, "La de Arias.")));
        })).isInstanceOf(ConsultationChangedException.class);

        assertThat(adapter.findById(consultation.id()).orElseThrow().addenda())
                .extracting(Addendum::text)
                .containsExactly("La de Benítez.");
    }
}
