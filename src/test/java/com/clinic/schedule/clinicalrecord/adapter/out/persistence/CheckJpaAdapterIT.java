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

import com.clinic.schedule.clinicalrecord.domain.Check;
import com.clinic.schedule.clinicalrecord.domain.CheckId;
import com.clinic.schedule.clinicalrecord.domain.CheckResult;
import com.clinic.schedule.clinicalrecord.domain.PatientId;
import com.clinic.schedule.clinicalrecord.domain.PractitionerId;
import com.clinic.schedule.clinicalrecord.domain.exception.ResultAlreadyRecordedException;

/**
 * What only the storage can show: two results recorded at the same time for one pending check do
 * not replace each other — the second is refused (D-53). The second transaction is opened inside
 * the first, so both read the check while it is still pending.
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
        })).isInstanceOf(ResultAlreadyRecordedException.class);

        Check read = adapter.findById(pending.id()).orElseThrow();
        assertThat(read.result()).get().extracting(CheckResult::text).isEqualTo("El del bioquímico.");
        assertThat(read.description()).isEqualTo("Extracción de sangre");
    }
}
