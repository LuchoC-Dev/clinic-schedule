package com.clinic.schedule.clinicalrecord.adapter.out.persistence;

import java.util.List;
import java.util.Optional;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Repository;

import com.clinic.schedule.clinicalrecord.application.port.CheckOrderRepository;
import com.clinic.schedule.clinicalrecord.domain.CheckOrder;
import com.clinic.schedule.clinicalrecord.domain.CheckOrderId;
import com.clinic.schedule.clinicalrecord.domain.OrderOutcome;
import com.clinic.schedule.clinicalrecord.domain.PatientId;
import com.clinic.schedule.clinicalrecord.domain.PractitionerId;
import com.clinic.schedule.clinicalrecord.domain.exception.OrderNotOpenException;

/** Implements the port over JPA. The only place that knows orders live in a table. */
@Repository
class CheckOrderJpaAdapter implements CheckOrderRepository {

    private final CheckOrderRowRepository rows;

    CheckOrderJpaAdapter(CheckOrderRowRepository rows) {
        this.rows = rows;
    }

    @Override
    public Optional<CheckOrder> findById(CheckOrderId id) {
        return rows.findById(id.value()).map(CheckOrderRow::toOrder);
    }

    @Override
    public List<CheckOrder> findAllForPatient(PatientId patientId) {
        return rows.findByPatientId(patientId.value()).stream().map(CheckOrderRow::toOrder).toList();
    }

    @Override
    public List<CheckOrder> findAllIssuedBy(PractitionerId practitionerId) {
        return rows.findByOrderedBy(practitionerId.value()).stream().map(CheckOrderRow::toOrder).toList();
    }

    /**
     * A new order is inserted whole. An existing one only changes its status (D-55): written onto the
     * row read earlier in the same transaction, whose version then says whether someone else
     * fulfilled or cancelled it meanwhile. The database also refuses one check fulfilling two orders.
     * It flushes at once, so either refusal is answered here, as the {@link OrderNotOpenException}
     * it means.
     */
    @Override
    public void save(CheckOrder order) {
        try {
            CheckOrderRow row = rows.findById(order.id().value())
                    .map(existing -> {
                        existing.statusFrom(order);
                        return existing;
                    })
                    .orElseGet(() -> CheckOrderRow.from(order));
            rows.saveAndFlush(row);
        } catch (ObjectOptimisticLockingFailureException e) {
            throw new OrderNotOpenException(order.id());
        } catch (DataIntegrityViolationException e) {
            if (order.outcome().orElse(null) instanceof OrderOutcome.Fulfilled) {
                throw new OrderNotOpenException(order.id());
            }
            throw e;
        }
    }
}
