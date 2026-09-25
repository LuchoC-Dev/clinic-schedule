package com.clinic.schedule.clinicalrecord.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDateTime;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import com.clinic.schedule.clinicalrecord.application.exception.CheckChangedException;
import com.clinic.schedule.clinicalrecord.domain.Addendum;
import com.clinic.schedule.clinicalrecord.domain.AddendumId;
import com.clinic.schedule.clinicalrecord.domain.Check;
import com.clinic.schedule.clinicalrecord.domain.CheckId;
import com.clinic.schedule.clinicalrecord.domain.CheckResult;
import com.clinic.schedule.clinicalrecord.domain.PatientId;
import com.clinic.schedule.clinicalrecord.domain.PractitionerId;

/**
 * What only the storage can show: two results, or two addenda, written at the same time on one
 * check do not replace each other — the second is refused (D-53, D-58). The second transaction is
 * opened inside the first, so both read the check before either writes.
 */
@SpringBootTest
class CheckJpaAdapterIT {

    private static final LocalDateTime AT = LocalDateTime.of(2026, 3, 10, 10, 15);

    @Autowired
    private CheckJpaAdapter adapter;

    @Autowired
    private PlatformTransactionManager transactions;

    private final PractitionerId arias = new PractitionerId(UUID.randomUUID());
    private final PractitionerId analyst = new PractitionerId(UUID.randomUUID());

    private TransactionTemplate inTransaction(int propagation) {
        TransactionTemplate template = new TransactionTemplate(transactions);
        template.setPropagationBehavior(propagation);
        return template;
    }

    @Test
    void twoResultsAtTheSameTimeDoNotReplaceEachOther() {
        Check pending = Check.perform(CheckId.newId(), new PatientId(UUID.randomUUID()), arias, AT,
                "Extracción de sangre", null);
        inTransaction(TransactionDefinition.PROPAGATION_REQUIRED).executeWithoutResult(status -> adapter.save(pending));

        assertThatThrownBy(() -> inTransaction(TransactionDefinition.PROPAGATION_REQUIRED).executeWithoutResult(slow -> {
            Check readBySlow = adapter.findById(pending.id()).orElseThrow();

            inTransaction(TransactionDefinition.PROPAGATION_REQUIRES_NEW).executeWithoutResult(fast -> {
                Check readByFast = adapter.findById(pending.id()).orElseThrow();
                adapter.save(readByFast.withResult(new CheckResult(analyst, AT.plusDays(1), "El del bioquímico.")));
            });

            adapter.save(readBySlow.withResult(new CheckResult(arias, AT.plusDays(1), "El de Arias.")));
        })).isInstanceOf(CheckChangedException.class);

        Check read = adapter.findById(pending.id()).orElseThrow();
        assertThat(read.result()).get().extracting(CheckResult::text).isEqualTo("El del bioquímico.");
        assertThat(read.description()).isEqualTo("Extracción de sangre");
    }

    @Test
    void twoAddendaAtTheSameTimeDoNotOverwriteEachOtherAndComeBackInOrder() {
        Check reported = Check.performWithResult(CheckId.newId(), new PatientId(UUID.randomUUID()), arias, AT,
                "Microalbuminuria", null, "Resultado equivocado");
        inTransaction(TransactionDefinition.PROPAGATION_REQUIRED).executeWithoutResult(status -> adapter.save(reported));

        assertThatThrownBy(() -> inTransaction(TransactionDefinition.PROPAGATION_REQUIRED).executeWithoutResult(slow -> {
            Check readBySlow = adapter.findById(reported.id()).orElseThrow();

            inTransaction(TransactionDefinition.PROPAGATION_REQUIRES_NEW).executeWithoutResult(fast -> {
                Check readByFast = adapter.findById(reported.id()).orElseThrow();
                adapter.save(readByFast.withAddendum(
                        new Addendum(AddendumId.newId(), analyst, AT.plusDays(1), "Corresponde a otro paciente.")));
            });

            adapter.save(readBySlow.withAddendum(
                    new Addendum(AddendumId.newId(), arias, AT.plusDays(1), "La de Arias.")));
        })).isInstanceOf(CheckChangedException.class);

        inTransaction(TransactionDefinition.PROPAGATION_REQUIRED).executeWithoutResult(status -> {
            Check read = adapter.findById(reported.id()).orElseThrow();
            adapter.save(read.withAddendum(new Addendum(AddendumId.newId(), arias, AT.plusDays(2), "Revisado.")));
        });
        assertThat(adapter.findById(reported.id()).orElseThrow().addenda())
                .extracting(Addendum::text)
                .containsExactly("Corresponde a otro paciente.", "Revisado.");
    }
}
