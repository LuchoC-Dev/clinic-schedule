package com.clinic.schedule.clinicalrecord.application.port;

import java.util.List;
import java.util.Optional;

import com.clinic.schedule.clinicalrecord.domain.CheckOrder;
import com.clinic.schedule.clinicalrecord.domain.CheckOrderId;
import com.clinic.schedule.clinicalrecord.domain.PatientId;

/** Where orders for a check are kept (D-55). */
public interface CheckOrderRepository {

    Optional<CheckOrder> findById(CheckOrderId id);

    /** Every order for the patient, open or not. */
    List<CheckOrder> findAllForPatient(PatientId patientId);

    /**
     * Stores a new order, or the new state of one read in the same transaction.
     *
     * @throws com.clinic.schedule.clinicalrecord.domain.exception.OrderNotOpenException when someone
     *         else fulfilled or cancelled it meanwhile
     */
    void save(CheckOrder order);
}
