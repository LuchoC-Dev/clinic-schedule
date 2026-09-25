package com.clinic.schedule.clinicalrecord.application.usecase;

import com.clinic.schedule.clinicalrecord.application.exception.UnknownOrderException;
import com.clinic.schedule.clinicalrecord.application.port.CheckOrderRepository;
import com.clinic.schedule.clinicalrecord.domain.Check;
import com.clinic.schedule.clinicalrecord.domain.CheckOrder;
import com.clinic.schedule.clinicalrecord.domain.CheckOrderId;

/**
 * Marks an order fulfilled by a check just recorded, for the use cases that record checks. The
 * order must be one of the check's patient's; the rest — open, done after it was issued — is the
 * order's own rule (D-55). Package-private: a detail of those use cases, inside their transaction.
 */
final class OrderFulfilment {

    private OrderFulfilment() {
    }

    /**
     * @throws UnknownOrderException when there is no such order for the check's patient
     * @throws com.clinic.schedule.clinicalrecord.domain.exception.OrderNotOpenException when it
     *         was already fulfilled or cancelled
     */
    static void fulfil(CheckOrderRepository orders, CheckOrderId orderId, Check check) {
        CheckOrder order = orders.findById(orderId)
                .filter(found -> found.patientId().equals(check.patientId()))
                .orElseThrow(() -> new UnknownOrderException(orderId));
        orders.save(order.fulfilledBy(check));
    }
}
