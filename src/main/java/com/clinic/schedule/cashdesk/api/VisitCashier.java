package com.clinic.schedule.cashdesk.api;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * What the cash desk is told when reception puts a patient into a queue (D-87): a visit starts, charged in
 * the same step, or left pending when it is an emergency. The queues call it inside their own transaction,
 * so a refused charge keeps the patient out.
 */
public interface VisitCashier {

    /**
     * @param visitId the patient's entry in the queue: the visit is that entry
     * @param amount the pesos charged; null only for an emergency, whose charge is left pending
     * @param means how they were paid; null for cash (D-89)
     * @throws ChargeRefusedException when the visit cannot start with that charge
     */
    void visitStarted(UUID visitId, UUID patientId, boolean emergency, BigDecimal amount, Means means);
}
