package com.clinic.schedule.clinicalrecord.adapter.in.screen;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.clinic.schedule.clinicalrecord.application.exception.ClinicalRecordAccessDeniedException;
import com.clinic.schedule.clinicalrecord.application.usecase.CancelOrderUseCase;
import com.clinic.schedule.clinicalrecord.domain.CheckOrderId;
import com.clinic.schedule.clinicalrecord.domain.exception.NotTheOrderAuthorException;
import com.clinic.schedule.clinicalrecord.domain.exception.OrderNotOpenException;

/**
 * Cancelling an order from the clinical record screen, with a reason (D-55). It goes back to the
 * record; a refusal comes back with the reason typed, and anyone but the author gets a 403.
 */
@Controller
class OrderViewController {

    private final CancelOrderUseCase cancel;

    OrderViewController(CancelOrderUseCase cancel) {
        this.cancel = cancel;
    }

    @PostMapping("/orders/{id}/cancel")
    String cancel(@PathVariable UUID id, @RequestParam UUID patientId, @RequestParam(required = false) String reason,
            RedirectAttributes flash) {
        String problem = null;
        if (reason == null || reason.isBlank()) {
            problem = "Escribí el motivo para anular la orden.";
        } else {
            try {
                cancel.handle(new CancelOrderUseCase.Command(new CheckOrderId(id), reason));
            } catch (ClinicalRecordAccessDeniedException | NotTheOrderAuthorException e) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, e.getMessage());
            } catch (OrderNotOpenException e) {
                problem = "Esa orden ya no está abierta: se cumplió o se anuló recién.";
            } catch (IllegalArgumentException e) {
                problem = "El motivo tiene hasta 500 caracteres.";
            }
        }
        if (problem != null) {
            flash.addFlashAttribute("error", problem);
            flash.addFlashAttribute("cancelFor", id);
            flash.addFlashAttribute("cancelReason", reason);
        } else {
            flash.addFlashAttribute("message", "Orden anulada.");
        }
        return "redirect:/clinical-records/" + patientId;
    }
}
