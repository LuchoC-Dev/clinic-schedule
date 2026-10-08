package com.clinic.schedule.cashdesk.api;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * What the cash desk is told when reception puts a patient into a queue (D-87): a visit starts, charged in
 * the same step, or covered by the patient's health insurance (D-90), or left pending when it is an emergency. The queues call it inside their own transaction,
 * so a refused charge keeps the patient out.
 */
public interface VisitCashier {

    /**
     * @param visitId the patient's entry in the queue: the visit is that entry
     * @param amount the pesos charged, the copay when covered; null only for an emergency, whose charge is left
     *        pending, or a covered visit with no copay
     * @param means how they were paid; null for cash (D-89)
     * @param coverage null when the patient pays privately (D-90)
     * @throws ChargeRefusedException when the visit cannot start with that charge or that coverage
     */
    void visitStarted(UUID visitId, UUID patientId, boolean emergency, BigDecimal amount, Means means,
            Coverage coverage);
}
