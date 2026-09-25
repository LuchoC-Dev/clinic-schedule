package com.clinic.schedule.clinicalrecord.adapter.in.http;

import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.clinic.schedule.clinicalrecord.application.usecase.CancelOrderUseCase;
import com.clinic.schedule.clinicalrecord.domain.CheckOrderId;
import com.clinic.schedule.practitioner.api.PractitionerReader;
import com.clinic.schedule.practitioner.api.PractitionerSummary;

import jakarta.validation.Valid;

/**
 * Orders for a check, for practitioners only (D-55). They are issued with a consultation and
 * fulfilled by a check, so the only operation of their own is the cancellation, by their author.
 */
@RestController
class OrderController {

    private final CancelOrderUseCase cancel;
    private final PractitionerReader practitioners;

    OrderController(CancelOrderUseCase cancel, PractitionerReader practitioners) {
        this.cancel = cancel;
        this.practitioners = practitioners;
    }

    /** Cancels an open order, with a reason; only its author may. A business operation, so a {@code POST} (D-23). */
    @PostMapping("/api/orders/{id}/cancel")
    CheckOrderResponse cancel(@PathVariable UUID id, @Valid @RequestBody CancelOrderRequest request) {
        Map<UUID, String> names = practitioners.findAll().stream()
                .collect(Collectors.toMap(PractitionerSummary::id, PractitionerSummary::fullName));
        return CheckOrderResponse.of(
                cancel.handle(new CancelOrderUseCase.Command(new CheckOrderId(id), request.reason())), names);
    }
}
