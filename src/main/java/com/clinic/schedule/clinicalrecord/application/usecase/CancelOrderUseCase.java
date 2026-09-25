package com.clinic.schedule.clinicalrecord.application.usecase;

import java.time.Clock;
import java.time.LocalDateTime;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.clinic.schedule.clinicalrecord.application.exception.ClinicalRecordAccessDeniedException;
import com.clinic.schedule.clinicalrecord.application.port.CheckOrderRepository;
import com.clinic.schedule.clinicalrecord.application.port.CurrentPractitionerPort;
import com.clinic.schedule.clinicalrecord.domain.CheckOrder;
import com.clinic.schedule.clinicalrecord.domain.CheckOrderId;
import com.clinic.schedule.clinicalrecord.domain.PractitionerId;

/**
 * The author of an open order cancels it, with a reason, by the practice's clock (D-55). Anyone
 * else is refused, and so is an order that does not exist, so the answer never tells whether it
 * does.
 */
@Service
public class CancelOrderUseCase {

    private final CheckOrderRepository orders;
    private final CurrentPractitionerPort currentPractitioner;
    private final Clock clock;

    public CancelOrderUseCase(CheckOrderRepository orders, CurrentPractitionerPort currentPractitioner, Clock clock) {
        this.orders = orders;
        this.currentPractitioner = currentPractitioner;
        this.clock = clock;
    }

    /**
     * @throws com.clinic.schedule.clinicalrecord.domain.exception.NotTheOrderAuthorException when
     *         the user did not issue it
     * @throws com.clinic.schedule.clinicalrecord.domain.exception.OrderNotOpenException when it was
     *         already fulfilled or cancelled
     */
    @Transactional
    public CheckOrder handle(Command command) {
        PractitionerId practitionerId = currentPractitioner.practitionerId()
                .orElseThrow(ClinicalRecordAccessDeniedException::toRead);
        CheckOrder order = orders.findById(command.orderId()).orElseThrow(ClinicalRecordAccessDeniedException::toRead);

        CheckOrder cancelled = order.cancelled(practitionerId, LocalDateTime.now(clock), command.reason());
        orders.save(cancelled);
        return cancelled;
    }

    public record Command(CheckOrderId orderId, String reason) {
    }
}
