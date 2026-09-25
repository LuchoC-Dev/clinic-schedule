package com.clinic.schedule.clinicalrecord.adapter.in.http;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.clinic.schedule.clinicalrecord.application.usecase.CancelOrderUseCase;
import com.clinic.schedule.clinicalrecord.application.usecase.ListMyOrdersUseCase;
import com.clinic.schedule.clinicalrecord.domain.CheckOrderId;
import com.clinic.schedule.patient.api.PatientReader;
import com.clinic.schedule.patient.api.PatientSummary;
import com.clinic.schedule.practitioner.api.PractitionerReader;
import com.clinic.schedule.practitioner.api.PractitionerSummary;

import jakarta.validation.Valid;

/**
 * Orders for a check, for practitioners only (D-55, D-56). They are issued with a consultation and
 * fulfilled by a check; of their own they have the cancellation, by their author, and the list the
 * author follows them up with.
 */
@RestController
class OrderController {

    private final CancelOrderUseCase cancel;
    private final ListMyOrdersUseCase myOrders;
    private final PractitionerReader practitioners;
    private final PatientReader patients;

    OrderController(CancelOrderUseCase cancel, ListMyOrdersUseCase myOrders, PractitionerReader practitioners,
            PatientReader patients) {
        this.cancel = cancel;
        this.myOrders = myOrders;
        this.practitioners = practitioners;
        this.patients = patients;
    }

    /** Cancels an open order, with a reason; only its author may. A business operation, so a {@code POST} (D-23). */
    @PostMapping("/api/orders/{id}/cancel")
    CheckOrderResponse cancel(@PathVariable UUID id, @Valid @RequestBody CancelOrderRequest request) {
        return CheckOrderResponse.of(
                cancel.handle(new CancelOrderUseCase.Command(new CheckOrderId(id), request.reason())),
                practitionerNames());
    }

    /**
     * The orders the user issued and did not cancel, with where each stands — open, waiting for its
     * result, reported — and whether the user may still read the record (D-56). No result texts.
     */
    @GetMapping("/api/orders/mine")
    List<MyOrderResponse> mine() {
        List<ListMyOrdersUseCase.MyOrder> found = myOrders.handle();
        Map<UUID, PatientSummary> patientsById = patients.findAll(found.stream()
                        .map(mine -> mine.order().patientId().value())
                        .distinct()
                        .toList())
                .stream()
                .collect(Collectors.toMap(PatientSummary::id, Function.identity()));
        Map<UUID, String> names = practitionerNames();
        return found.stream().map(mine -> MyOrderResponse.of(mine, patientsById, names)).toList();
    }

    private Map<UUID, String> practitionerNames() {
        return practitioners.findAll().stream()
                .collect(Collectors.toMap(PractitionerSummary::id, PractitionerSummary::fullName));
    }
}
